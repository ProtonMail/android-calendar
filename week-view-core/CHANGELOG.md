Changelog
=========

## Version 5.4.0
*(2022-11-15)*

- New: Allow use of custom time zone for the view
- New: Added new hour time separators
- New: Added background to all day expand info label.
- New: Added attr to hide arrow down icon.
- New: Added support to expand all day header by clicking the + x more label
- New: Dynamically reduce vertical padding to adjust the event title. Hide the title if it doesn't fit the event chip after padding reaches 0.
- New: Added events styling (past, unanswered, declined, cancelled, failed to decrypt).
- New: Added events side strip
- New: Added time format support.
- New: Added today square to date header label
- New: Added header click handlers (on empty view click and on date click)
- New: Display "No events" label in day view
- New: Display "Loading events" label in day view header
- New: Added custom week start support.
- New: Added setDate and setDateTime to change date without scrolling.
- New: Added minimum height of 10min for event blobs.
- New: Added callback after we finish zooming so we can extract and save the value.
- New: Display part day multi day events as all day events.
- New: Added styling for part day multi day events (start time as prefix and occurrence number in day view)
- New: Handle all day and part day multi day events by drawing them as continuous chips.
- New: Allow zero duration events
- New: Try to fill empty spaces when header contains multi day events.

- Changed: Event ids type from Long to String.
- Changed: Set now line at user calendar settings timezone.
- Changed: Updated arrow icon.
- Changed: Updated day header labels style.
- Changed: Updated week number style
- Changed: Updated max number of all day events before having to expand
- Changed: Improved horizontal scroll and fling in three days view
- Changed: Reworked event chips columns computing logic.
- Changed: Ellipsize all day events titles
- Changed: Updated header vertical separators
- Changed: Hide now time indicator if date is not in currently visible date range
- Changed: Merged emoji, base and jsr310 modules in core module

## Earlier releases
- See: [forked repository](https://github.com/thellmund/Android-Week-View)
- See: [original repository](https://github.com/alamkanak/Android-Week-View)
