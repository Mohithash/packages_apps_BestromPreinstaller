# BestromPreinstaller

Android has no "removable preload" mechanism: PRODUCT_PACKAGES ships an APK
either as a normal system app (which reappears after every OTA / factory
reset and generally can't be uninstalled by the user) or not at all. This app
is the workaround - a privileged, product_specific system app with no UI that
installs a set of bundled third-party APKs into the user's normal app space
on first boot, using the same public `PackageInstaller` API any installer app
(e.g. Play Store) uses. Once installed, the APKs are indistinguishable from
anything the user installed themselves: they show up in Settings > Apps,
they can be force-stopped, and - critically - they can be uninstalled and
**stay** uninstalled across reboots, because this app tracks per-package
"done" state and never reinstalls a package it has already handled.

## Dropping APKs in

This app does not ship any APKs itself. Add them to the image separately,
via `PRODUCT_COPY_FILES` from a vendor tree, targeting:

```
/product/etc/bestrom/preinstall/*.apk
```

(`/system/etc/bestrom/preinstall/` is also scanned, as a fallback, for
builds that copy files to `/system` instead of `/product`.)

On first boot after BOOT_COMPLETED, every `*.apk` found there is installed
as a normal removable user app via `PackageInstaller`
(`INSTALL_REASON_DEVICE_SETUP`). A package is skipped - and never revisited -
once it has been handled once, whether that means "we installed it" or "it
was already installed by other means". This is what makes the whole thing
stick: a user uninstall on a later boot will not be undone.

See `src/org/bestrom/preinstaller/Preinstaller.java` for the implementation.
