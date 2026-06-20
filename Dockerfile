FROM openjdk:17-jdk-slim

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# 设置环境变量
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 下载并安装 Android SDK Command Line Tools
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip commandlinetools-linux-9477386_latest.zip && \
    rm commandlinetools-linux-9477386_latest.zip && \
    mv cmdline-tools latest

# 接受 Android SDK 许可
RUN yes | sdkmanager --licenses || true

# 安装必要的 Android SDK 组件
RUN sdkmanager --update && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 设置工作目录
WORKDIR /app

# 复制项目文件
COPY android/ /app/android/

# 构建APK
RUN cd android && \
    curl -L https://services.gradle.org/distributions/gradle-8.2-bin.zip -o gradle.zip && \
    unzip gradle.zip && \
    rm gradle.zip && \
    ./gradle-8.2/bin/gradle assembleRelease --stacktrace

# 输出APK
RUN cp android/app/build/outputs/apk/release/app-release.apk /app/

CMD ["bash"]
