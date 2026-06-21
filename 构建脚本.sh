#!/bin/bash
# ============================================================
#  构建脚本 - 将源码编译成APK
#  天女目瑛 制作
#  使用方法：在终端执行 bash 构建脚本.sh
# ============================================================

set -e

echo "🏗️ 开始构建杏林一键评教APK..."

# ========== 路径配置 ==========
源码目录="$(cd "$(dirname "$0")" && pwd)"
构建目录="$源码目录/构建临时目录"
输出目录="$源码目录/已编译APK"

# 工具路径
AAPT="/usr/bin/aapt"
D8_JAR="/tmp/r8.jar"
APKSIGNER="/usr/bin/apksigner"
ANDROID_JAR="/tmp/android.jar"
JAVA="/usr/bin/java"

# ========== 准备构建目录 ==========
echo "📁 准备构建目录..."
rm -rf "$构建目录"
mkdir -p "$构建目录/classes" "$构建目录/assets" "$构建目录/res"

# ========== 编译Java源码 ==========
echo "☕ 编译Java源码..."
$JAVA -cp "$ANDROID_JAR" -d "$构建目录/classes" \
    "$源码目录/MainActivity.java" 2>&1

# ========== 编译DEX ==========
echo "📦 编译DEX字节码..."
# D8的--output参数需要指定一个目录（不是.dex文件）
mkdir -p "$构建目录/dexout"
$JAVA -cp "$D8_JAR" com.android.tools.r8.D8 \
    --output "$构建目录/dexout" \
    --lib "$ANDROID_JAR" \
    $(find "$构建目录/classes" -name "*.class") 2>&1
# 把classes.dex复制到构建目录根目录
cp "$构建目录/dexout/classes.dex" "$构建目录/classes.dex"

# ========== 打包资源 ==========
echo "🎨 打包资源..."
# 注意：assets目录中的文件名必须全英文，否则aapt会报错
$AAPT package -f \
    --min-sdk-version 23 \
    -M "$源码目录/AndroidManifest.xml" \
    -S "$源码目录/res" \
    -A "$源码目录/assets" \
    -I "$ANDROID_JAR" \
    -F "$构建目录/app.apk" 2>&1

# ========== 添加DEX到APK ==========
echo "📦 添加DEX到APK..."
cd "$构建目录"
$AAPT add app.apk classes.dex 2>&1
cd "$源码目录"

# ========== 生成签名密钥 ==========
echo "🔑 生成签名密钥..."
KEYSTORE="$构建目录/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -alias androiddebugkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" 2>&1
fi

# ========== 签名APK（使用jarsigner，兼容性更好） ==========
echo "✍️ 签名APK..."
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    "$构建目录/app.apk" androiddebugkey 2>&1

# ========== 复制到输出目录 ==========
echo "📂 复制APK到输出目录..."
mkdir -p "$输出目录"
cp "$构建目录/app.apk" "$输出目录/杏林一键评教.apk"

# ========== 清理 ==========
rm -rf "$构建目录"

echo ""
echo "========================================"
echo "🎉 构建完成！"
echo "📦 APK文件位置：$输出目录/杏林一键评教.apk"
echo "========================================"
ls -lh "$输出目录/杏林一键评教.apk"