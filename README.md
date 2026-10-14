# FVST Express Driver (Android)

Kotlin + Jetpack Compose app for FVST Express drivers: task list, consolidated pickups,
drop-offs with photo proof of delivery, failed deliveries, inbox, hours and breaks. Built from the
`mockup-driver` pages and talks to the admin app's `/driver-api/v1`.

## Open and run

1. Open this folder in Android Studio (the same version that builds `MyApplication`:
   AGP 9.4.1, Kotlin 2.2.10, Gradle 9.6, compile SDK 37).
2. Let Gradle sync, then Run on an emulator or phone.
3. Log in with a driver's employee card # (or phone) and the PIN set in the admin under
   Drivers › Edit driver › Driver app PIN. Everything you see after login is live data from the server.

## Server settings: `config.properties`

All server settings are in **`config.properties`** at the project root. Change a value and rebuild.

| Key | What it does |
|---|---|
| `api.baseUrl` | Driver API address, ending in `/driver-api/v1/`. `http://10.0.2.2/driver-api/v1/` = your PC from the emulator. On a real phone, use your PC's LAN IP, e.g. `http://192.168.1.20/driver-api/v1/`. For the live server, use its `https://` address. |
| `api.hostHeader` | `fvstexpress-admin.local` makes the app send WAMP's virtual host name, which Symfony requires (trusted hosts). Leave it empty when `api.baseUrl` already uses the real host name. |
| `api.useMock` | `false` (default) = real API. `true` = demo mode with the mockup sample data and no server; any login + PIN works. |
| `dispatch.phone` | Number for "Call Dispatch" until the server sends one (`DRIVER_APP_DISPATCH_PHONE` in the admin `.env.local`). |

For one build only, any key can be overridden from the command line, e.g.
`./gradlew assembleDebug -Pfvst.api.baseUrl=https://your-admin-domain.com/driver-api/v1/`.

## Admin side

1. In the admin project, run the migrations: `php bin/console doctrine:migrations:migrate`
2. Give a driver a PIN under **Drivers › Edit driver › Driver app PIN**
   (or `php bin/console app:driver:set-pin EMP-00412 1234`).
3. Every call the app makes is listed under **Drivers › Driver API Log**, with what was sent and what
   came back (PINs and tokens hidden). Use it to see what a driver did or why a tap failed.

## Postman

`postman/` has the collection, a Local (WAMP) and a Production environment, and `sample-proof.jpg`
for the photo upload. The folders run top to bottom as one driver day (log in → tasks → pick up →
deliver with photo → fail the second drop-off → messages → end of day → log out). Set `driverLogin` /
`driverPin` in the environment first. From the command line:
`newman run FVST-Express-Driver-API.postman_collection.json -e FVST-Driver-Local.postman_environment.json --working-dir .`
(the same files are also in the admin project's `postman/` folder).

## Project layout

```
app/src/main/java/com/fvstexpress/driver/
  data/api        DriverApi (Retrofit), ApiFactory (auth, token refresh, Host header), FakeDriverApi (demo)
  data/model      JSON models (snake_case on the wire)
  data/repo       DriverRepository: every call, error messages, offline queueing of taps
  data/offline    OfflineQueue + SyncWorker: taps and photos saved without signal, replayed in order
  data/location   LocationService: foreground GPS while Online -> POST /presence
  data/session    SessionStore: tokens + driver in DataStore
  ui/...          Login, Tasks (+ route view), Stop (pickup / delivery / done), Failed, Analytics,
                  Profile (hours, breaks), Messages, Connectivity (status + Report Issue)
config.properties Server settings (API URL, Host header, demo mode, dispatch phone)
postman/          Postman collection + environments for the same API
```

## Not done yet

- Push notifications (FCM) need a Firebase project and `google-services.json`; until then the task
  list refreshes every minute and on pull-down.
- The route view lists stops in order; a real map needs geocoded addresses on the server (OQ-023).
- Release build: switch the server to HTTPS and turn off cleartext in `res/xml/network_security_config.xml`.
