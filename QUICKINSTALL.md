# QuickInstall cheat sheet

For when you've already read `INSTALL.md` once and just need the fast path.
No explanations here — see `INSTALL.md` if anything below doesn't make sense.

---

## Before you sit down

- [ ] Download the latest `stayawake-apk` artifact from the GitHub Actions
      run (Actions tab → "Build watch app" → latest green run → Artifacts),
      and **unzip it** so you have `app-debug.apk` sitting somewhere you can
      reach from a terminal.
- [ ] Platform-tools (`adb`) installed and unzipped on your computer.
- [ ] Watch and computer on the **same Wi-Fi network**.

---

## Watch-side prep

1. Settings → About watch → Software → tap **Software version** 5×
   (turns on developer mode).
2. Settings → **Developer options** → turn on **ADB debugging** and
   **Wireless debugging**.

---

## Command sequence

1. On the watch: Wireless debugging → **Pair new device** → note the code
   and `IP:PORT` shown there.

   ```
   adb pair IP:PORT
   ```
   (enter the pairing code when prompted)

2. Back on the **main** Wireless debugging screen (not the pair screen) —
   note the **other** `IP:PORT` shown there:

   ```
   adb connect IP:PORT
   ```

3. Confirm it's connected:

   ```
   adb devices
   ```

4. Install:

   ```
   adb install -r app-debug.apk
   ```

**Windows:** commands are `adb ...` as above.
**Mac/Linux:** prefix each with `./`, e.g. `./adb pair IP:PORT`.

---

## Two watches connected at once

If both your old Galaxy Watch and the new Ultra show up in `adb devices`,
target the one you want by serial/address:

```
adb devices
adb -s IP:PORT install -r app-debug.apk
```

(`IP:PORT` here is whatever `adb devices` printed for that watch — copy it
exactly.)

---

## Troubleshooting one-liners

- **Pairing code expired** → close and reopen "Pair new device" on the
  watch for a fresh code, then retry `adb pair`.
- **Device shows `offline`** → toggle Wireless debugging off/on on the
  watch, then `adb connect IP:PORT` again with the current address.
- **"Allow debugging from this computer?" popup** → tap **Always allow**.
