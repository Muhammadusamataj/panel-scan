# Panel Scan

Panel (Android head unit) par chalne wali read-only scanner app. Kuch badalti nahi.
Scan karke report ek chhote web link par rakh deti hai, jo phone/laptop ke browser se khulta hai.

## APK banana (cloud mein, PC par kuch install nahi)
1. GitHub par naya **public** repo banayein (naam: panel-scan).
2. Is folder ki saari files upload karein (`.github` folder samet).
   Ya Claude Code mein kahein: "is folder ko GitHub repo panel-scan par push karo".
3. Repo ke **Actions** tab mein `build-apk` chalega (3-5 minute). Green hone ka intezar karein.
4. APK link (panel ke browser mein kholein):
   https://github.com/<aap-ka-username>/panel-scan/releases/download/latest/app-debug.apk

## Panel par
1. Link se APK download karein, install karein (Unknown sources allow karna paray ga).
2. "Panel Scan" app kholen. Scan khud shuru hoga (30-90 second).
3. Screen par jo link aaye (http://192.168.x.x:8080/) wo phone ya laptop ke browser mein kholen
   (ek hi WiFi). "Download panel_scan.txt" dabayein.
4. panel_scan.txt Claude ko bhej dein.

App khuli rakhni hai jab tak report download na ho.
