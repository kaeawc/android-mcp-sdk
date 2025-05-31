# Use Azul Zulu OpenJDK 23 as base image
FROM azul/zulu-openjdk:23-latest

# Set environment variables
ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools
ENV JAVA_HOME=/usr/lib/jvm/zulu-23

# Install essential packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    wget \
    unzip \
    git \
    build-essential \
    xmlstarlet \
    shellcheck \
    && rm -rf /var/lib/apt/lists/*

# Create Android SDK directory
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools

# Download and install Android Command Line Tools
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp \
    && mv /tmp/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

# Accept Android SDK licenses
RUN yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1

# Install essential Android SDK components (without emulator)
RUN ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" \
    "build-tools;34.0.0" \
    "platforms;android-34" \
    "platforms;android-33" \
    "platforms;android-32" \
    "platforms;android-31" \
    "platforms;android-30" \
    "platforms;android-29" \
    "cmake;3.22.1" \
    "ndk;25.2.9519653"

# Install ktfmt for Kotlin formatting
RUN mkdir -p /usr/local/bin \
    && wget -q https://github.com/facebook/ktfmt/releases/download/v0.54/ktfmt-0.54-jar-with-dependencies.jar -O /usr/local/bin/ktfmt.jar \
    && echo '#!/bin/bash\njava -jar /usr/local/bin/ktfmt.jar "$@"' > /usr/local/bin/ktfmt \
    && chmod +x /usr/local/bin/ktfmt

# Install act for local GitHub Actions testing
RUN ARCH=$(uname -m) \
    && case $ARCH in \
        x86_64) ARCH="x86_64" ;; \
        aarch64|arm64) ARCH="arm64" ;; \
        *) echo "Unsupported architecture: $ARCH" && exit 1 ;; \
    esac \
    && wget -q https://github.com/nektos/act/releases/download/v0.2.68/act_Linux_$ARCH.tar.gz -O /tmp/act.tar.gz \
    && tar -xzf /tmp/act.tar.gz -C /tmp \
    && mv /tmp/act /usr/local/bin/ \
    && chmod +x /usr/local/bin/act \
    && rm /tmp/act.tar.gz

# Set working directory
WORKDIR /workspace

# Create a non-root user for better security
RUN groupadd -r android && useradd -r -g android -m android \
    && chown -R android:android ${ANDROID_HOME} \
    && chown -R android:android /workspace

# Switch to non-root user
USER android

# Verify installations
RUN java -version \
    && ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --version \
    && ktfmt --version \
    && act --version \
    && xmlstarlet --version \
    && shellcheck --version

# Set default command
CMD ["/bin/bash"]