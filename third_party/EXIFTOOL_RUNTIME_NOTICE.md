# Bundled ExifTool runtime

EmreShots executes the real ExifTool command-line application on-device through its bundled Android Perl runtime.

- Source project: https://github.com/bestvibes/exiftoolwrapper-android
- Release: release-20260610
- Artifact: ExifToolWrapper-release-20260610-arm64-v8a.apk
- SHA-256: 47851825ec30601fce2edb3d9ff023bc7aa748344ae99aee417769df9742286

The release workflow imports only the verified libperl runtime libraries and perl5.tar asset. EmreShots invokes the bundled exiftool script directly through ProcessBuilder; it does not invoke a shell.

ExifTool is distributed under its applicable Artistic/GPL licensing terms. See https://exiftool.org/ for the upstream source and license information.

The repository also contains native/build.sh and native/PINS for a reproducible source-build path used for future runtime refreshes.
