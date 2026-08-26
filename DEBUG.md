# Collecting diagnostics for analysis

If something goes wrong during a monitoring session — the alarm doesn't fire,
heart rate looks wrong, or the watch seems to stop tracking — the app keeps a
detailed log you can pull afterward. This page explains what's recorded and
how to get it off the watch, step by step. No prior debugging experience
needed.

---

## What gets recorded

Every time you start a monitoring session, the app writes one line per event
to a log file on the watch, until you stop the session (or the app is
killed). It records:

- **Session start/stop** — when monitoring began and ended, which mode
  (manual or scheduled) and sensitivity was used.
- **Screen on/off** — every time the watch screen turns on or off, plus the
  screen state at the moment monitoring started.
- **Battery level** — a percentage reading once a minute while monitoring is
  running.
- **Heart rate readings** — each heart-rate sample, along with the current
  baseline, how long you've been still, and how long HR has been low.
- **Motion** — a motion-activity number every ~5 seconds from the
  accelerometer.
- **Exercise tracking status** — whether fast heart-rate batching was
  available for this session, and any errors starting exercise tracking.
- **Passive/backstop signal** — the watch's own "user activity state"
  (e.g. asleep) as reported by the passive monitoring API.
- **Alarm fired / alarm dismissed** — when the wake-up alarm was triggered
  and when you dismissed it.
- **Errors** — anything that went wrong internally (exercise tracking
  failures, registration failures, etc.).

Only the **last 20 sessions** are kept — older log files are deleted
automatically when a new session starts, so the watch never fills up.

---

## How to pull the logs after a session

You'll need your watch connected to your computer via `adb` — if it's not
connected yet, follow the connection steps in `QUICKINSTALL.md` first
(pairing / `adb connect`), then come back here.

1. Confirm the watch is connected:

   ```
   adb devices
   ```

2. Pull the whole logs folder to your computer:

   ```
   adb pull /sdcard/Android/data/com.samibi.stayawake/files/logs stayawake-logs
   ```

   This copies everything into a new `stayawake-logs` folder next to wherever
   you ran the command.

   If that path doesn't work on your watch, list what's actually there first:

   ```
   adb shell ls /sdcard/Android/data/com.samibi.stayawake/files/logs
   ```

3. **Two watches connected at once?** Add `-s <serial>` right after `adb`,
   using whatever `adb devices` printed for the watch you want, e.g.:

   ```
   adb -s 192.168.1.42:5555 pull /sdcard/Android/data/com.samibi.stayawake/files/logs stayawake-logs
   ```

---

## Also grab the system log if something misbehaved

Right after you notice a problem (alarm didn't go off, app crashed, etc.),
also capture the last couple thousand lines of the system log:

```
adb logcat -d -t 2000 > logcat.txt
```

Do this **before** restarting the app or the watch — restarting can wipe the
buffer this reads from.

---

## What to send back for analysis

1. The **newest** `session-*.jsonl` file from the `stayawake-logs` folder you
   pulled (it's named with the session's start time, e.g.
   `session-20260814-233015.jsonl` — pick the most recent one).
2. `logcat.txt`, if you captured one because something misbehaved.
3. One sentence about what you actually experienced and roughly when, e.g.
   *"alarm never vibrated while bedtime mode was on at ~23:40"*.

That's it — no need to read or interpret the log yourself.

---

## What each problem looks like in the log (for reference)

If you're curious or want to peek yourself, here's roughly how common
problems show up in the `.jsonl` file:

| Problem | What you'll see in the log |
|---|---|
| Alarm suppressed by sleep mode | An `alarm_fired` line is present, but no `alarm_dismissed` follows and you never actually felt a vibration. |
| Heart-rate gaps while screen is off | A `screen` line with `"on": false`, followed by a gap of more than ~15 seconds between `hr` lines (instead of the usual frequent readings). |
| Service killed by battery saver | The log simply ends partway through a session — no `session_stop` line at the end. |
| Slow heart-rate batching | The `exercise` line shows `"fastBatching": false` — heart-rate updates will arrive less often than expected. |

---

## Reading battery drain

The `battery` lines are logged once a minute with the current battery
percentage (`"pct"`). To estimate drain rate, compare the `pct` value at the
start and end of an hour-long stretch — the difference is roughly your
battery drain per hour during monitoring.

---

## Working from a different network (e.g. at the office)

You can install and pull logs from anywhere, as long as your computer and the
watch are on the **same Wi-Fi**. Nothing about the app changes — but a few
things about the network do.

**1. Check the network actually allows it.** Many office and guest Wi-Fi
networks use "client isolation", which blocks devices from talking to each
other even though they're on the same network. Test before anything else:

```
ping <watch-ip>
```

(The watch's IP is on its Wireless debugging screen.) If there's no reply,
wireless adb will not work on that network. Use a phone hotspot for both
devices instead, or wait until you're home. On a company network, also check
that this is allowed by your IT policy.

**2. Make sure the watch is really on Wi-Fi.** Galaxy Watches often drop Wi-Fi
while connected to the phone over Bluetooth. Check Settings → Connections →
Wi-Fi on the watch.

**3. Expect a new IP and a fresh pairing.** On a different network the watch
gets a different IP address, so pair and connect again:

```
adb pair <ip>:<pairing-port>
adb connect <ip>:<connect-port>
```

### If the computer doing the work isn't the one near the watch

For example, the watch is next to your laptop, but the project lives on your
home server that you reach over Tailscale/VPN. Run the adb server on the
laptop and tunnel to it over SSH:

```
# on the remote machine, open a tunnel to the laptop:
ssh -L 5037:localhost:5037 <user>@<laptop-hostname>

# then, in another shell on the remote machine:
export ADB_SERVER_SOCKET=tcp:localhost:5037
adb devices        # lists the watch connected to the laptop
```

Every normal adb command then works from the remote machine.

**Do not** use `adb -a nodaemon server` to share adb instead. That exposes adb
on every network interface, and on a shared office network anyone on that
network could install apps on your watch. The SSH tunnel above keeps it
private.
