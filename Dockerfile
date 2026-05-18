FROM ghcr.io/cirruslabs/android-sdk:35

WORKDIR /workspace
COPY . /workspace

WORKDIR /workspace/android

RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true

# Gradle wrapper is committed; no system Gradle required.
RUN chmod +x gradlew && ./gradlew --no-daemon assembleRelease

# APK remains at app/build/outputs/apk/release/app-release.apk inside the image.
# Copy to host with: docker compose run --rm extract
