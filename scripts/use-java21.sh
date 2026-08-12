#!/usr/bin/env bash
# 把当前 shell 的 JAVA_HOME 钉在 JDK 21 上。
#
# 用法（注意是 source，不是执行）：
#     source scripts/use-java21.sh
#
# 为什么需要：本机默认 JDK 是 26，而 Spring Boot 3.5 / Spring Framework 6.2
# 的官方支持上限没到 26。Spring 内部用 ASM 读取 class 文件，遇到未知的
# class file version 会抛出与业务完全无关的异常，极难定位。
#
# 兜底：即使忘了 source，pom.xml 里的 maven-enforcer-plugin 也会在构建一开始
# 就报错退出，不会让你带着错误的 JDK 跑出一屏怪异堆栈。

JAVA_21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)"

if [ -z "$JAVA_21_HOME" ]; then
  echo "✗ 未找到 JDK 21。已安装的 JDK：" >&2
  /usr/libexec/java_home -V >&2
  return 1 2>/dev/null || exit 1
fi

export JAVA_HOME="$JAVA_21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "✓ JAVA_HOME = $JAVA_HOME"
java -version
