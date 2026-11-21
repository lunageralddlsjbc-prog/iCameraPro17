@rem Execute Gradle
"%JAVA_HOME%\bin\java.exe" "-Dorg.gradle.appname=%~nx0" -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
