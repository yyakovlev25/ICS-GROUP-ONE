
# PMS 1.0.0 – Running the Application (Windows & Linux)

This document explains how to use the `pms-1.0.0.zip` distribution and how to start the PMS application on **Windows** and **Linux/macOS**.

---

## 1. ZIP Contents

After extracting `pms-1.0.0.zip`, the following structure is created:

```
pms-1.0.0/
 ├─ pms-1.0.0.jar        # Application JAR
 ├─ lib/                 # Required dependency JARs
 │   └─ *.jar
 └─ bin/
     ├─ run.sh           # Start script for Linux/macOS
     └─ run.bat          # Start script for Windows
```

---

## 2. Prerequisites

- Java **21** (or compatible)
- One of the following must be available:
  - `JAVA_HOME` environment variable
  - `JAVA_CMD` pointing to the Java executable
  - `java` / `java.exe` available on the system `PATH`

---

## 3. Running on Linux / macOS

```bash
unzip pms-1.0.0.zip
cd pms-1.0.0
./bin/run.sh
```

Optional:
```bash
export JAVA_HOME=/opt/jdk-21
```

---

## 4. Running on Windows

```bat
cd pms-1.0.0\bin
.\run.bat
```

Optional:
```bat
set JAVA_HOME=C:\Program Files\Java\jdk-21
```

---

## 5. Main Class

The application starts using:

```
de.mbg.pms.ServerMain
```

All dependencies are loaded from the `lib` directory.
