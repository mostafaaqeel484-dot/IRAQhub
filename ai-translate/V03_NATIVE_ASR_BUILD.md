# AI Translate V0.3 build

Replaces unreliable platform SpeechRecognizer / EXTRA_AUDIO_SOURCE with direct AudioRecord 16 kHz PCM and SenseVoice int8 offline recognition. English and Japanese to Arabic via ML Kit. Diagnostic RMS status displayed only in app, subtitles only as overlay. Build via .github/workflows/ai-translate-build.yml. Model comes from upstream k2-fsa release at CI time. Not yet confirmed on Xiaomi 14T.
