package me.proton.android.calendar.presentation.importAssistant.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.common.CalendarForm
import me.proton.android.calendar.common.CalendarImport
import me.proton.android.calendar.common.utils.AndroidUtils.ellipsize
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.ColorUtils
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarMappingEntity
import me.proton.android.calendar.data.api.ImporterEntity
import me.proton.android.calendar.data.api.ReportEntity
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.ImporterApi
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.ImportCalendarMapping
import me.proton.android.calendar.domain.usecase.CreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import javax.inject.Inject

@HiltViewModel
class ImportAssistantViewModel @Inject constructor(
    application: Application,
    private val logger: Logger,
    private val accountManager: AccountManager,
    private val userManager: UserManager,
    private val userAddressManager: UserAddressManager,
    private val importerApi: ImporterApi,
    private val createCalendarUseCase: CreateCalendarUseCase,
    private val updateCalendarSettingsUseCase: UpdateCalendarSettingsUseCase,
    private val calendarsRepository: CalendarsRepository
) : AndroidViewModel(application) {

    private val _importCalendarMappingList: MutableLiveData<List<ImportCalendarMapping>?> = MutableLiveData()
    val importCalendarMappingList: LiveData<List<ImportCalendarMapping>?> = _importCalendarMappingList

    private val _sourceEmail: MutableLiveData<String?> = MutableLiveData()
    val sourceEmail: LiveData<String?> = _sourceEmail

    private val _importerList: MutableLiveData<List<ImporterEntity>?> = MutableLiveData()
    val importerList: LiveData<List<ImporterEntity>?> = _importerList

    private val _reportList: MutableLiveData<List<ReportEntity>?> = MutableLiveData()
    val reportList: LiveData<List<ReportEntity>?> = _reportList

    val defaultUserEmail: MutableLiveData<String?> = MutableLiveData()

    private lateinit var importerId: String

    val importSnackState: MutableStateFlow<ImportSnackState?> = MutableStateFlow(null)
    val importGuideSnackState: MutableStateFlow<ImportSnackState?> = MutableStateFlow(null)
    val importStatusSnackState: MutableStateFlow<ImportSnackState?> = MutableStateFlow(null)

    sealed class ImportSnackState {

        data class DisplaySnack(
            val message: String
        ): ImportSnackState()
    }

    sealed class ImportResult {
        object Success : ImportResult()
        class Error(val userErrorMessage: String? = null) : ImportResult()
    }

    private suspend fun getPrimaryUserIdOrNull() = accountManager.getPrimaryUserId().firstOrNull()

    fun resetViewModel() {
        _importerList.value = null
        _reportList.value = null
        _importCalendarMappingList.value = null
        _sourceEmail.value = null
    }

    suspend fun getGoogleSignInOptions(): GoogleSignInOptions? {
        val userId = getPrimaryUserIdOrNull() ?: return null

        // Fetch google client Id from config
        val clientId = importerApi.getGoogleClientId(userId).valueOrNullAndLogErrors(logger)?.config?.googleClientId ?: return null

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestServerAuthCode(clientId)
            .requestScopes(Scope(CalendarImport.GOOGLE_CALENDAR_SCOPE))
            .requestEmail()
            .build()
    }

    private suspend fun getDefaultUserEmail(): String? {
        val userId = getPrimaryUserIdOrNull() ?: return null

        val userAddresses = userAddressManager.getAddressesOrNull(userId) ?: emptyList()

        val defaultUserEmail = userManager.getUser(userId).email
        val defaultUserAddress =
            if (defaultUserEmail != null) {
                userAddresses.find {
                    ProtonUtilsImpl.canonicalizeProtonEmail(
                        it.email,
                        forceCanonicalization = true
                    ) == ProtonUtilsImpl.canonicalizeProtonEmail(
                        defaultUserEmail,
                        forceCanonicalization = true
                    )
                }
            } else null

        return if (defaultUserEmail != null && defaultUserAddress != null && defaultUserAddress.enabled && defaultUserAddress.canReceive && defaultUserAddress.canSend) {
            defaultUserEmail
        } else {
            val userEmails = userAddresses.filter { it.enabled && it.canSend && it.canReceive }.sortedBy { it.order }.map { it.email }
            userEmails.firstOrNull()
        }
    }

    suspend fun handleGoogleSignInRedirect(userId: UserId, code: String, colorValuesArray: Array<String>, importerId: String? = null): Boolean {
        // Create Access token resource
        val token = importerApi.createAccessToken(userId, code).valueOrNullAndLogErrors(logger)?.token ?: return false
        val tokenId = token.id
        _sourceEmail.value = token.account

        return if (importerId != null) {
            updateImporter(userId, tokenId, importerId, token.account)
        } else {
            createImporter(userId, tokenId, token.account, colorValuesArray)
        }
    }

    private suspend fun createImporter(userId: UserId, tokenId: String, account: String, colorValuesArray: Array<String>): Boolean {
        // Create the importer for the required products
        importerId = importerApi.createCalendarImporter(userId, tokenId).valueOrNullAndLogErrors(logger)?.importerID ?: return false

        // Get all the importer mapping info
        val externalCalendarList = importerApi.getCalendarImportMappingInfo(userId, importerId).valueOrNullAndLogErrors(logger)?.calendars ?: return false

        defaultUserEmail.value = getDefaultUserEmail() ?: run {
            logger.e("ImportAssistantViewModel createImporter failed to get default user email")
            return false
        }

        // Map external calendars to ImportCalendarMapping list
        val importCalendarMappingList = arrayListOf<ImportCalendarMapping>()
        externalCalendarList.forEach {
            importCalendarMappingList.add(
                ImportCalendarMapping(
                    importCalendar = true, // Set to true by default
                    sourceId = it.id,
                    sourceName = it.source.ellipsize(100),
                    sourceEmail = account,
                    sourceDescription = it.description,
                    createDestinationCalendar = true, // Set to true by default
                    destinationId = null, // Set to null by default (calendar has yet to be created)
                    destinationName = it.source.ellipsize(100),
                    destinationEmail = defaultUserEmail.value!!,
                    destinationDescription = it.description.ellipsize(255),
                    destinationColor = colorValuesArray.random()
                )
            )
        }
        _importCalendarMappingList.value = importCalendarMappingList

        return true
    }

    private suspend fun updateImporter(userId: UserId, tokenId: String, importerId: String, sourceEmail: String): Boolean {

        val importer = getImporter(importerId) ?: run {
            logger.e("ImportAssistantViewModel updateImporter failed to get importer")
            return false
        }

        if (importer.account != sourceEmail) {
            logger.e("ImportAssistantViewModel updateImporter incorrect google account")
            return false
        }

        // Update the importer with the new token id
        importerApi.updateCalendarImporter(userId, importerId, tokenId).valueOrNullAndLogErrors(logger) ?: return false

        // Resume import
        return if (resumeImport(importerId) is ImportResult.Success) {
            if (_importerList.value?.any { it.id == importerId } == true) {
                // Refresh importers list if it has the importer
                getImporters()
            }
            true
        } else false
    }

    suspend fun startImport(customCalendarMapping: Boolean, importCalendarMappingList: List<ImportCalendarMapping>): ImportResult {
        val userId = getPrimaryUserIdOrNull() ?: return ImportResult.Error()

        // Map ImportCalendarMapping list to CalendarMappingEntity list
        val calendarMapping = importCalendarMappingList.mapNotNull {
            if (it.destinationId != null) {
                CalendarMappingEntity(
                    source = it.sourceId,
                    destination = it.destinationId!!
                )
            } else null
        }

        // Start importer
        return when (val startImporterResponse = importerApi.startImporter(userId, importerId, customCalendarMapping, calendarMapping)) {
            is ApiResponse.Success -> {
                ImportResult.Success
            }
            is ApiResponse.Error -> {
                ImportResult.Error(userErrorMessage = startImporterResponse.error)
            }
            is ApiResponse.Exception -> {
                ImportResult.Error()
            }
        }
    }

    suspend fun createCalendar(
        calendarName: String,
        calendarDescription: String,
        calendarEmail: String,
        calendarColor: String
    ): String? {
        val userId = getPrimaryUserIdOrNull() ?: return null

        // Create calendar
        val createCalendarResult = createCalendarUseCase.execute(
            userId = userId,
            name = calendarName,
            description = calendarDescription,
            color = calendarColor,
            email = calendarEmail
        )
        if (createCalendarResult !is UseCase.Result.Success<*>) {
            createCalendarResult.ifSuccessAndLogErrors(logger) {}
            return null
        }

        createCalendarResult.returnValue.tryCast<String> {
            val calendarId = this

            // Update newly created calendar settings
            val defaultPartDayAlarms = arrayListOf(CalendarForm.DEFAULT_PART_DAY_ALARM)
            defaultPartDayAlarms.add(CalendarForm.DEFAULT_PART_DAY_EMAIL_ALARM)
            val defaultAllDayAlarms = arrayListOf(CalendarForm.DEFAULT_ALL_DAY_ALARM)
            defaultAllDayAlarms.add(CalendarForm.DEFAULT_ALL_DAY_EMAIL_ALARM)

            val updateCalendarSettingsUseCaseResult = updateCalendarSettingsUseCase.updateCalendarSettings(
                userId,
                calendarId,
                CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.first(),
                defaultPartDayAlarms,
                defaultAllDayAlarms
            )
            if (updateCalendarSettingsUseCaseResult !is UseCase.Result.Success<*>) {
                logger.i("ImportAssistantViewModel createCalendar failed to update calendar settings")
                return calendarId // Calendar has still been created
            }

            return calendarId
        }

        return null
    }

    fun setImportCalendarMappingList(calendarsToImport: List<ImportCalendarMapping>) {
        _importCalendarMappingList.value = calendarsToImport
    }

    fun setImportCalendar(calendarToImport: ImportCalendarMapping, importCalendar: Boolean): Int? {
        val currentList = _importCalendarMappingList.value ?: return null
        val indexOfItem = currentList.indexOf(calendarToImport)
        if (indexOfItem < 0 || indexOfItem > currentList.lastIndex) return null
        currentList[indexOfItem].importCalendar = importCalendar
        _importCalendarMappingList.value = currentList
        return indexOfItem
    }

    suspend fun setCreateNewCalendar(calendarToImport: ImportCalendarMapping, calendarColor: String) {
        val defaultUserEmail = defaultUserEmail.value ?: getDefaultUserEmail() ?: return
        val updatedCalendarToImport = ImportCalendarMapping(
            importCalendar = true,
            sourceId = calendarToImport.sourceId,
            sourceName = calendarToImport.sourceName,
            sourceEmail = calendarToImport.sourceEmail,
            sourceDescription = calendarToImport.sourceDescription,
            createDestinationCalendar = true,
            destinationId = null,
            destinationName = calendarToImport.sourceName,
            destinationEmail = defaultUserEmail,
            destinationDescription = calendarToImport.sourceDescription,
            destinationColor = calendarColor
        )
        val currentList = _importCalendarMappingList.value?.let { ArrayList(it) } ?: return
        val indexOfItem = currentList.indexOf(calendarToImport)
        // Replace previous item
        currentList.removeAt(indexOfItem)
        currentList.add(indexOfItem, updatedCalendarToImport)
        _importCalendarMappingList.value = currentList
    }

    fun setMergeExistingCalendar(calendarToImport: ImportCalendarMapping, calendar: Calendar) {
        val updatedCalendarToImport = ImportCalendarMapping(
            importCalendar = true,
            sourceId = calendarToImport.sourceId,
            sourceName = calendarToImport.sourceName,
            sourceEmail = calendarToImport.sourceEmail,
            sourceDescription = calendarToImport.sourceDescription,
            createDestinationCalendar = false,
            destinationId = calendar.id,
            destinationName = calendar.name,
            destinationEmail = calendar.email,
            destinationDescription = calendar.description,
            destinationColor = calendar.color
        )
        val currentList = _importCalendarMappingList.value?.let { ArrayList(it) } ?: return
        val indexOfItem = currentList.indexOf(calendarToImport)
        if (indexOfItem < 0 || indexOfItem > currentList.lastIndex) return
        // Replace previous item
        currentList.removeAt(indexOfItem)
        currentList.add(indexOfItem, updatedCalendarToImport)
        _importCalendarMappingList.value = currentList
    }

    suspend fun getImporters() {
        val userId = getPrimaryUserIdOrNull() ?: return

        val importers = importerApi.getImporters(userId).valueOrNullAndLogErrors(logger)?.importers ?: return
        _importerList.value = importers
    }

    suspend fun isImportInProgress(accountEmail: String): Boolean {
        val userId = getPrimaryUserIdOrNull() ?: return false

        val importers = importerApi.getImporters(userId).valueOrNullAndLogErrors(logger)?.importers ?: return false
        return importers.any { it.account == accountEmail && it.active?.calendar != null }
    }

    private suspend fun getImporter(importerId: String): ImporterEntity? {
        val userId = getPrimaryUserIdOrNull() ?: return null

        return importerApi.getImporter(userId, importerId).valueOrNullAndLogErrors(logger)?.importer
    }

    suspend fun getReports() {
        val userId = getPrimaryUserIdOrNull() ?: return

        val reports = importerApi.getReports(userId).valueOrNullAndLogErrors(logger)?.reports ?: return
        _reportList.value = reports
    }

    suspend fun cancelImport(importId: String): Boolean {
        val userId = getPrimaryUserIdOrNull() ?: return false

        importerApi.cancelImport(userId, importId).valueOrNullAndLogErrors(logger) ?: return false
        return true
    }

    suspend fun resumeImport(importId: String): ImportResult {
        val userId = getPrimaryUserIdOrNull() ?: return ImportResult.Error()

        // Start importer
        return when (val resumeImportResponse = importerApi.resumeImport(userId, importId)) {
            is ApiResponse.Success -> {
                ImportResult.Success
            }
            is ApiResponse.Error -> {
                ImportResult.Error(userErrorMessage = resumeImportResponse.error)
            }
            is ApiResponse.Exception -> {
                ImportResult.Error()
            }
        }
    }

    suspend fun deleteReport(reportId: String): Boolean {
        val userId = getPrimaryUserIdOrNull() ?: return false

        importerApi.deleteReport(userId, reportId).valueOrNullAndLogErrors(logger) ?: return false

        // Update report list
        val currentList = _reportList.value?.let { ArrayList(it) } ?: return false
        currentList.removeIf { it.id == reportId }
        _reportList.value = currentList
        return true
    }
}
