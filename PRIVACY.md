# Privacy Policy — Pesto

Last updated: 2 September 2026

Pesto is a personal, open-source Android Auto app for Greek voice commands.
It has no backend, no analytics, no advertising, and no user accounts.

## What Pesto does with your data

**Voice.** When you speak to Pesto, the audio is passed to Android's built-in
speech recognition service (`SpeechRecognizer`) to be turned into text. Greek
recognition has no offline model, so that service sends the audio to Google
for processing under [Google's privacy policy](https://policies.google.com/privacy).
Pesto does not record, store or transmit audio itself. Recognised text is held
in memory only for as long as it takes to act on it.

**Contacts.** When you ask Pesto to call someone, it reads your contacts from
the phone to find the matching name. This happens entirely on the device.
Contacts are never uploaded, stored by Pesto, or shared with anyone.

**Phone calls.** Pesto places calls through Android's dialer. It does not
listen to, record, or log calls.

**Destinations.** When you ask for navigation, the recognised text is handed
to your maps app, which takes over from there under its own privacy policy.

## What Pesto does not do

Pesto has no server. It collects nothing, stores nothing between sessions,
and sends nothing anywhere except the two hand-offs described above — the
system speech recognizer, and your maps or dialer app.

## Permissions

- `RECORD_AUDIO` — to hear your command
- `READ_CONTACTS` — to match a spoken name to a contact
- `CALL_PHONE` — to place the call you asked for

## Source code

Pesto is open source under GPL-3.0: https://github.com/altany/mila

## Contact

Questions: open an issue at https://github.com/altany/mila/issues
