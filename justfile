# Both APKs share one applicationId (Data Layer requirement).
app_id := "dev.falcop.betterwatchcalendar"

default:
    @just --list

# List connected devices
devices:
    adb devices -l

# Pair with a device over wireless debugging (prompts for the pairing code)
pair addr:
    adb pair {{addr}}

connect addr:
    adb connect {{addr}}

install-phone:
    ANDROID_SERIAL="$PHONE_SERIAL" ./gradlew :mobile:installDebug

install-watch:
    ANDROID_SERIAL="$WATCH_SERIAL" ./gradlew :wear:installDebug

install: install-phone install-watch

log-phone:
    adb -s "$PHONE_SERIAL" logcat --pid="$(adb -s "$PHONE_SERIAL" shell pidof -s {{app_id}})"

log-watch:
    adb -s "$WATCH_SERIAL" logcat --pid="$(adb -s "$WATCH_SERIAL" shell pidof -s {{app_id}})"

test:
    ./gradlew test

fmt:
    ./gradlew ktfmtFormat

lint:
    ./gradlew ktfmtCheck detekt lint

# Create the shared debug keystore at $BWC_DEBUG_KEYSTORE (outside the repo) if missing
keystore:
    test -f "$BWC_DEBUG_KEYSTORE" || keytool -genkeypair -v \
        -keystore "$BWC_DEBUG_KEYSTORE" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"

# Launch Android Studio with the shell's JDK and SDK
studio:
    android-studio . >/dev/null 2>&1 &
