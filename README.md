# Proton Calendar for Android 

This is the Proton Calendar client for Android for managing events and calendars on your Android devices.

## Set up

Open the project in the latest Android Studio.
Run Gradle sync if not prompted and let Gradle download the dependencies. The dependencies are all available publicly.

## Build variants

### Commonly used variants

| Scheme        | Description                                                                                            |
|:--------------|:-------------------------------------------------------------------------------------------------------|
| `devDebug`    | Debug variant, points to the Black API environment (only accessible through the internal Proton VPN)   |
| `devRelease`  | Release variant, points to the Black API environment (only accessible through the internal Proton VPN) |
| `prodDebug`   | Debug variant, points to the production API environment                                                |
| `prodRelease` | Release variant, points to the production API environment                                              |

### Testing

Run tests:
`./gradlew testDebugUnitTest testDevDebugUnitTest`

## Modules

### App

The main app module. Contains most of the calendar functionalities.

### Shared test code

Testing utilities to be used in the app module.

### Week view core

An adaptation of https://github.com/alamkanak/Android-Week-View used for week view display (day-view, 3-day-view, week-view).

## Contributing notice

This project does not accept external contributions.

## License

The code and data files in this distribution are licensed under the terms of the GPLv3 as published by the Free Software Foundation. See https://www.gnu.org/licenses/ for a copy of this license.
