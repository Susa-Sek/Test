# JavaMail sucht Anbieter (IMAP, SMTP) ueber META-INF/javamail.providers und laedt sie
# per Reflexion. Ohne diese Regeln faende ein verkleinerter Build kein einziges Protokoll.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.**
