;------------------------------------------------------------------------------
; Invoice OCR - Windows setup
;
; Installs the application, silently installs the bundled Tesseract OCR setup
; at its own default location, then discovers where that landed and points the
; application at it.
;
; Nothing about the Tesseract install path is assumed: the payload is a
; third-party NSIS installer whose default directory can differ between builds
; and between per-machine and per-user installs, so this script looks the
; location up afterwards and says so plainly if it cannot find usable language
; data.
;
; Build:
;   powershell -ExecutionPolicy Bypass -File build-installer.ps1
;
; Or by hand:
;   makensis /DTESSERACT_SETUP=C:\path\tesseract-ocr-w64-setup-5.5.3.20260724.exe invoice-ocr.nsi
;------------------------------------------------------------------------------

Unicode true

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "WordFunc.nsh"
!include "x64.nsh"

;------------------------------------------------------------------ definitions

!define APP_NAME        "Invoice OCR"
!define APP_VERSION     "1.1.1"
!define APP_PUBLISHER   "Invoice OCR"
!define APP_URL         "https://github.com/tesseract-ocr/tesseract"
!define APP_JAR_NAME    "invoice-ocr.jar"
!define APP_REG_KEY     "Software\InvoiceOCR"
!define UNINST_KEY      "Software\Microsoft\Windows\CurrentVersion\Uninstall\InvoiceOCR"
!define MIN_JAVA        17

; Payload locations, overridable from the command line with /D
!ifndef APP_JAR
  !define APP_JAR "..\target\invoice-ocr.jar"
!endif
!ifndef TESSERACT_SETUP
  !error "TESSERACT_SETUP is not defined. Pass /DTESSERACT_SETUP=<path to tesseract-ocr-w64-setup-*.exe>, or run build-installer.ps1 which finds it for you."
!endif
!ifndef LANG_DATA_DIR
  !define LANG_DATA_DIR "payload\tessdata"
!endif

Name "${APP_NAME} ${APP_VERSION}"
OutFile "invoice-ocr-setup-${APP_VERSION}.exe"
InstallDir "$PROGRAMFILES64\${APP_NAME}"
InstallDirRegKey HKLM "${APP_REG_KEY}" "InstallDir"
RequestExecutionLevel admin
SetCompressor /SOLID lzma
ShowInstDetails show
ShowUninstDetails show

VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey "ProductName"     "${APP_NAME}"
VIAddVersionKey "ProductVersion"  "${APP_VERSION}"
VIAddVersionKey "FileVersion"     "${APP_VERSION}.0"
VIAddVersionKey "CompanyName"     "${APP_PUBLISHER}"
VIAddVersionKey "FileDescription" "${APP_NAME} setup"
VIAddVersionKey "LegalCopyright"  "${APP_PUBLISHER}"

;------------------------------------------------------------------- variables

Var JavaWPath        ; full path to javaw.exe, empty when Java is missing
Var JavaMajor        ; detected major version, 0 when unknown
Var TessdataDir      ; the tessdata folder the application will be pointed at
Var TesseractDir     ; install root of Tesseract, when one was found
Var OwnTessdata      ; "1" when this installer created tessdata inside $INSTDIR

;----------------------------------------------------------------------- pages

!define MUI_ABORTWARNING
!define MUI_ICON "assets\invoice-ocr.ico"
!define MUI_UNICON "assets\invoice-ocr.ico"

!define MUI_WELCOMEPAGE_TITLE "${APP_NAME} ${APP_VERSION}"
!define MUI_WELCOMEPAGE_TEXT "This will install ${APP_NAME} on your computer.$\r$\n$\r$\nIt also installs the Tesseract OCR engine, which supplies the language data the application needs in order to read invoices. An existing Tesseract installation is detected and left alone.$\r$\n$\r$\nClose the application before continuing."
!insertmacro MUI_PAGE_WELCOME

!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES

!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION LaunchApplication
!define MUI_FINISHPAGE_RUN_TEXT "Start ${APP_NAME}"
!define MUI_FINISHPAGE_SHOWREADME "$INSTDIR\docs\index.html"
!define MUI_FINISHPAGE_SHOWREADME_TEXT "Open the user handbook"
!define MUI_FINISHPAGE_SHOWREADME_NOTCHECKED
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

;--------------------------------------------------------------------- helpers

; Accepts "<dir>" and sets $TessdataDir when "<dir>\tessdata" holds language data.
!macro TryTessdata dir
  ${If} $TessdataDir == ""
  ${AndIf} ${FileExists} "${dir}\tessdata\*.traineddata"
    StrCpy $TessdataDir "${dir}\tessdata"
    StrCpy $TesseractDir "${dir}"
  ${EndIf}
!macroend

; Strips surrounding double quotes from the string on top of the stack.
!macro StripQuotesBody
  Exch $R0
  Push $R1
  Push $R2
  StrCpy $R1 $R0 1
  ${If} $R1 == '"'
    StrCpy $R0 $R0 "" 1
    StrLen $R2 $R0
    IntOp $R2 $R2 - 1
    StrCpy $R0 $R0 $R2
  ${EndIf}
  Pop $R2
  Pop $R1
  Exch $R0
!macroend

Function StripQuotes
  !insertmacro StripQuotesBody
FunctionEnd

Function un.StripQuotes
  !insertmacro StripQuotesBody
FunctionEnd

; Converts backslashes to forward slashes. A .properties file reads a backslash
; as an escape character, so a Windows path must never be written raw into one.
Function PathToSlashes
  Exch $R0
  Push $R1
  Push $R2
  Push $R3
  StrCpy $R1 ""
  StrCpy $R2 0
  ${Do}
    StrCpy $R3 $R0 1 $R2
    ${If} $R3 == ""
      ${ExitDo}
    ${EndIf}
    ${If} $R3 == "\"
      StrCpy $R1 "$R1/"
    ${Else}
      StrCpy $R1 "$R1$R3"
    ${EndIf}
    IntOp $R2 $R2 + 1
  ${Loop}
  StrCpy $R0 $R1
  Pop $R3
  Pop $R2
  Pop $R1
  Exch $R0
FunctionEnd

; Canonical NSIS newline trimmer.
Function TrimNewlines
  Exch $R0
  Push $R1
  Push $R2
  StrCpy $R1 0
  loop:
    IntOp $R1 $R1 - 1
    StrCpy $R2 $R0 1 $R1
    StrCmp $R2 "$\r" loop
    StrCmp $R2 "$\n" loop
    IntOp $R1 $R1 + 1
    IntCmp $R1 0 no_trim_needed
    StrCpy $R0 $R0 $R1
  no_trim_needed:
  Pop $R2
  Pop $R1
  Exch $R0
FunctionEnd

; Fills $JavaMajor and $JavaWPath by asking the Java on PATH what it is.
Function DetectJava
  Push $0
  Push $1
  Push $2

  StrCpy $JavaMajor 0
  StrCpy $JavaWPath ""

  ; Output looks like:  openjdk version "21.0.9" 2025-10-21 LTS
  nsExec::ExecToStack '"$SYSDIR\cmd.exe" /c java -version 2>&1'
  Pop $0
  Pop $1
  ${If} $0 == 0
    ClearErrors
    ${WordFind} "$1" '"' "E+2" $2
    ${IfNot} ${Errors}
      ClearErrors
      ${WordFind} "$2" "." "E+1" $0
      ${IfNot} ${Errors}
        ${If} $0 == "1"
          ; Legacy scheme: 1.8.0_402 means 8
          ClearErrors
          ${WordFind} "$2" "." "E+2" $0
          ${IfNot} ${Errors}
            StrCpy $JavaMajor $0
          ${EndIf}
        ${Else}
          StrCpy $JavaMajor $0
        ${EndIf}
      ${EndIf}
    ${EndIf}
  ${EndIf}

  nsExec::ExecToStack '"$SYSDIR\cmd.exe" /c where javaw.exe'
  Pop $0
  Pop $1
  ${If} $0 == 0
    ClearErrors
    ${WordFind} "$1" "$\r$\n" "E+1" $2
    ${If} ${Errors}
      StrCpy $2 $1
    ${EndIf}
    Push $2
    Call TrimNewlines
    Pop $2
    ${If} ${FileExists} "$2"
      StrCpy $JavaWPath $2
    ${EndIf}
  ${EndIf}

  Pop $2
  Pop $1
  Pop $0
FunctionEnd

; Fills $TessdataDir with a folder that actually contains language data.
Function FindTessdata
  Push $0
  Push $1
  Push $2
  Push $3

  StrCpy $TessdataDir ""
  StrCpy $TesseractDir ""

  ; The usual locations first: cheap, and right almost every time.
  !insertmacro TryTessdata "$PROGRAMFILES64\Tesseract-OCR"
  !insertmacro TryTessdata "$PROGRAMFILES\Tesseract-OCR"
  !insertmacro TryTessdata "C:\Tesseract-OCR"

  ; Per-user installs live under the profile, which needs the current-user
  ; shell context: in all-users context $LOCALAPPDATA resolves to ProgramData.
  SetShellVarContext current
  !insertmacro TryTessdata "$LOCALAPPDATA\Programs\Tesseract-OCR"
  !insertmacro TryTessdata "$LOCALAPPDATA\Tesseract-OCR"
  !insertmacro TryTessdata "$APPDATA\Tesseract-OCR"
  SetShellVarContext all

  ; Otherwise ask the uninstall registry where Tesseract put itself. Both
  ; registry views and the per-user hive are searched, because the payload may
  ; install per machine or per user.
  ${If} $TessdataDir == ""
    StrCpy $3 0
    ${Do}
      ${If} $3 == 0
        SetRegView 64
      ${ElseIf} $3 == 1
        SetRegView 32
      ${EndIf}

      StrCpy $0 0
      ${Do}
        ${If} $3 < 2
          EnumRegKey $1 HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall" $0
        ${Else}
          EnumRegKey $1 HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall" $0
        ${EndIf}
        ${If} $1 == ""
          ${ExitDo}
        ${EndIf}
        IntOp $0 $0 + 1

        ${If} $3 < 2
          ReadRegStr $2 HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\$1" "DisplayName"
        ${Else}
          ReadRegStr $2 HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$1" "DisplayName"
        ${EndIf}
        StrCpy $2 $2 9
        ${If} $2 == "Tesseract"
          ${If} $3 < 2
            ReadRegStr $2 HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\$1" "InstallLocation"
          ${Else}
            ReadRegStr $2 HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$1" "InstallLocation"
          ${EndIf}
          ${If} $2 == ""
            ${If} $3 < 2
              ReadRegStr $2 HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\$1" "UninstallString"
            ${Else}
              ReadRegStr $2 HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$1" "UninstallString"
            ${EndIf}
            Push $2
            Call StripQuotes
            Pop $2
            ${GetParent} "$2" $2
          ${EndIf}
          !insertmacro TryTessdata "$2"
        ${EndIf}
      ${Loop}

      IntOp $3 $3 + 1
      ${If} $3 > 2
      ${OrIf} $TessdataDir != ""
        ${ExitDo}
      ${EndIf}
    ${Loop}
    SetRegView 64
  ${EndIf}

  Pop $3
  Pop $2
  Pop $1
  Pop $0
FunctionEnd

Function LaunchApplication
  ${If} $JavaWPath != ""
    ExecShell "" "$JavaWPath" '-jar "$INSTDIR\${APP_JAR_NAME}"' SW_SHOWNORMAL
  ${Else}
    ExecShell "open" "$INSTDIR\${APP_JAR_NAME}"
  ${EndIf}
FunctionEnd

;-------------------------------------------------------------------- sections

Section "!${APP_NAME}" SecApp
  SectionIn RO
  SetOutPath "$INSTDIR"
  SetOverwrite on

  DetailPrint "Installing application files..."
  File "/oname=${APP_JAR_NAME}" "${APP_JAR}"
  File "assets\invoice-ocr.ico"

  ; The user handbook travels with the application when it has been generated.
  SetOutPath "$INSTDIR\docs"
  File /nonfatal /r "..\user-docu\html\*.*"
  SetOutPath "$INSTDIR"
SectionEnd

Section "Tesseract OCR engine" SecTesseract
  ${If} $TessdataDir != ""
    DetailPrint "Tesseract is already installed: $TesseractDir"
  ${Else}
    DetailPrint "Installing Tesseract OCR, this takes a moment..."
    InitPluginsDir
    SetOutPath "$PLUGINSDIR"
    File "/oname=tesseract-setup.exe" "${TESSERACT_SETUP}"

    ; /S is the payload's own silent switch. No /D is passed on purpose, so the
    ; payload installs at its own default location.
    ExecWait '"$PLUGINSDIR\tesseract-setup.exe" /S' $0
    DetailPrint "Tesseract setup finished with exit code $0."

    SetOutPath "$INSTDIR"
    Call FindTessdata
    ${If} $TessdataDir != ""
      DetailPrint "Tesseract installed at: $TesseractDir"
    ${Else}
      DetailPrint "WARNING: no Tesseract language data found after installation."
    ${EndIf}
  ${EndIf}
SectionEnd

Section "Language data (English, Romanian)" SecLanguages
  ; Bundled *.traineddata files fill any gap, so a Tesseract install without
  ; Romanian still reads Romanian invoices, and a machine where the Tesseract
  ; step was skipped still ends up with working language data.
  ${If} $TessdataDir == ""
    DetailPrint "Using a local language folder: $INSTDIR\tessdata"
    CreateDirectory "$INSTDIR\tessdata"
    StrCpy $TessdataDir "$INSTDIR\tessdata"
    StrCpy $OwnTessdata "1"
  ${EndIf}

  SetOutPath "$TessdataDir"
  SetOverwrite off      ; never replace language data that is already there
  File /nonfatal "${LANG_DATA_DIR}\*.traineddata"
  SetOverwrite on
  SetOutPath "$INSTDIR"
SectionEnd

Section "Desktop shortcut" SecDesktop
  ${If} $JavaWPath != ""
    CreateShortcut "$DESKTOP\${APP_NAME}.lnk" "$JavaWPath" \
      '-jar "$INSTDIR\${APP_JAR_NAME}"' "$INSTDIR\invoice-ocr.ico" 0 SW_SHOWNORMAL "" "${APP_NAME}"
  ${Else}
    CreateShortcut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${APP_JAR_NAME}" "" \
      "$INSTDIR\invoice-ocr.ico" 0 SW_SHOWNORMAL "" "${APP_NAME}"
  ${EndIf}
SectionEnd

Section "-Finalize"
  ; --- Say so if there is still nothing to read invoices with -------------
  ${If} $TessdataDir == ""
  ${OrIfNot} ${FileExists} "$TessdataDir\*.traineddata"
    DetailPrint "ERROR: no OCR language data is available."
    ${IfNot} ${Silent}
      MessageBox MB_OK|MB_ICONEXCLAMATION \
        "No OCR language data could be found or installed.$\r$\n$\r$\n${APP_NAME} is installed, but cannot read invoices until a tessdata folder containing eng.traineddata exists and ocr.tessdata.path in$\r$\n$INSTDIR\invoice-ocr.properties points at it."
    ${EndIf}
  ${EndIf}

  ; --- Settings file ------------------------------------------------------
  ${If} ${FileExists} "$INSTDIR\invoice-ocr.properties"
    Delete "$INSTDIR\invoice-ocr.properties.bak"
    Rename "$INSTDIR\invoice-ocr.properties" "$INSTDIR\invoice-ocr.properties.bak"
    DetailPrint "Previous settings kept as invoice-ocr.properties.bak"
  ${EndIf}

  Push "$TessdataDir"
  Call PathToSlashes
  Pop $1

  StrCpy $2 "eng"
  ${If} ${FileExists} "$TessdataDir\ron.traineddata"
    StrCpy $2 "ron+eng"
  ${EndIf}

  FileOpen $0 "$INSTDIR\invoice-ocr.properties" w
  FileWrite $0 "# ${APP_NAME} settings, written by the installer.$\r$\n"
  FileWrite $0 "# Use forward slashes in paths. Restart the application after editing.$\r$\n"
  FileWrite $0 "$\r$\n"
  FileWrite $0 "ocr.tessdata.path=$1$\r$\n"
  FileWrite $0 "ocr.language=$2$\r$\n"
  FileWrite $0 "ui.locale=ro$\r$\n"
  FileClose $0
  DetailPrint "Language data: $TessdataDir (ocr.language=$2)"

  ; --- Start menu ---------------------------------------------------------
  SetShellVarContext all
  CreateDirectory "$SMPROGRAMS\${APP_NAME}"
  ${If} $JavaWPath != ""
    CreateShortcut "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" "$JavaWPath" \
      '-jar "$INSTDIR\${APP_JAR_NAME}"' "$INSTDIR\invoice-ocr.ico" 0 SW_SHOWNORMAL "" "${APP_NAME}"
  ${Else}
    CreateShortcut "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" "$INSTDIR\${APP_JAR_NAME}" "" \
      "$INSTDIR\invoice-ocr.ico" 0 SW_SHOWNORMAL "" "${APP_NAME}"
  ${EndIf}
  ${If} ${FileExists} "$INSTDIR\docs\index.html"
    CreateShortcut "$SMPROGRAMS\${APP_NAME}\User handbook.lnk" "$INSTDIR\docs\index.html"
  ${EndIf}
  CreateShortcut "$SMPROGRAMS\${APP_NAME}\Uninstall ${APP_NAME}.lnk" "$INSTDIR\uninstall.exe"

  ; --- Registry -----------------------------------------------------------
  WriteRegStr HKLM "${APP_REG_KEY}" "InstallDir" "$INSTDIR"
  WriteRegStr HKLM "${APP_REG_KEY}" "Version" "${APP_VERSION}"
  WriteRegStr HKLM "${APP_REG_KEY}" "TessdataPath" "$TessdataDir"
  WriteRegStr HKLM "${APP_REG_KEY}" "OwnTessdata" "$OwnTessdata"

  WriteRegStr HKLM "${UNINST_KEY}" "DisplayName" "${APP_NAME}"
  WriteRegStr HKLM "${UNINST_KEY}" "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKLM "${UNINST_KEY}" "DisplayIcon" "$INSTDIR\invoice-ocr.ico"
  WriteRegStr HKLM "${UNINST_KEY}" "Publisher" "${APP_PUBLISHER}"
  WriteRegStr HKLM "${UNINST_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "${UNINST_KEY}" "URLInfoAbout" "${APP_URL}"
  WriteRegStr HKLM "${UNINST_KEY}" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegStr HKLM "${UNINST_KEY}" "QuietUninstallString" '"$INSTDIR\uninstall.exe" /S'
  WriteRegDWORD HKLM "${UNINST_KEY}" "NoModify" 1
  WriteRegDWORD HKLM "${UNINST_KEY}" "NoRepair" 1

  WriteUninstaller "$INSTDIR\uninstall.exe"

  ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
  IntFmt $0 "0x%08X" $0
  WriteRegDWORD HKLM "${UNINST_KEY}" "EstimatedSize" "$0"
SectionEnd

;---------------------------------------------------------------- descriptions

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecApp} \
    "The application itself, plus the user handbook. Required."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecTesseract} \
    "Installs the bundled Tesseract OCR engine at its default location. Skipped automatically when Tesseract is already installed."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecLanguages} \
    "Adds the English and Romanian language files wherever they are missing."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecDesktop} \
    "Places a shortcut on the desktop."
!insertmacro MUI_FUNCTION_DESCRIPTION_END

;------------------------------------------------------------------ init hooks

Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_OK|MB_ICONSTOP "${APP_NAME} requires 64-bit Windows."
    Abort
  ${EndIf}
  SetRegView 64
  SetShellVarContext all

  StrCpy $OwnTessdata "0"

  Call DetectJava
  Call FindTessdata

  ${If} $JavaMajor < ${MIN_JAVA}
  ${AndIfNot} ${Silent}
    MessageBox MB_YESNO|MB_ICONEXCLAMATION \
      "Java ${MIN_JAVA} or newer is required, and was not found on this computer.$\r$\n$\r$\n${APP_NAME} will not start without it.$\r$\n$\r$\nOpen the Java download page now? Choose No to install anyway and set Java up afterwards." \
      IDNO javaHandled
    ExecShell "open" "https://adoptium.net/temurin/releases/?version=21"
    javaHandled:
  ${EndIf}
FunctionEnd

;-------------------------------------------------------------------- uninstall

Section "Uninstall"
  SetShellVarContext all
  SetRegView 64

  ReadRegStr $0 HKLM "${APP_REG_KEY}" "OwnTessdata"

  Delete "$INSTDIR\${APP_JAR_NAME}"
  Delete "$INSTDIR\invoice-ocr.ico"
  Delete "$INSTDIR\invoice-ocr.properties"
  Delete "$INSTDIR\invoice-ocr.properties.bak"
  Delete "$INSTDIR\uninstall.exe"
  RMDir /r "$INSTDIR\docs"

  ; Only remove language data that this installer created.
  ${If} $0 == "1"
    RMDir /r "$INSTDIR\tessdata"
  ${EndIf}
  RMDir "$INSTDIR"

  Delete "$DESKTOP\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\User handbook.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\Uninstall ${APP_NAME}.lnk"
  RMDir "$SMPROGRAMS\${APP_NAME}"

  DeleteRegKey HKLM "${UNINST_KEY}"
  DeleteRegKey HKLM "${APP_REG_KEY}"

  ; Tesseract is shared software: other programs may rely on it, so removing it
  ; is opt-in and never happens unattended.
  ${IfNot} ${Silent}
    Call un.FindTesseractUninstaller
    Pop $1
    ${If} $1 != ""
      MessageBox MB_YESNO|MB_ICONQUESTION \
        "Also remove Tesseract OCR?$\r$\n$\r$\nOther programs may be using it. Choose No if you are not sure." \
        IDNO skipTesseract
      DetailPrint "Removing Tesseract OCR..."
      ExecWait '"$1" /S' $2
      DetailPrint "Tesseract uninstaller finished with exit code $2."
      skipTesseract:
    ${EndIf}
  ${EndIf}
SectionEnd

; Pushes the path of the Tesseract uninstaller, or an empty string.
Function un.FindTesseractUninstaller
  StrCpy $R3 ""
  StrCpy $R0 0
  ${Do}
    EnumRegKey $R1 HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall" $R0
    ${If} $R1 == ""
      ${ExitDo}
    ${EndIf}
    IntOp $R0 $R0 + 1
    ReadRegStr $R2 HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\$R1" "DisplayName"
    StrCpy $R2 $R2 9
    ${If} $R2 == "Tesseract"
      ReadRegStr $R3 HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\$R1" "UninstallString"
      Push $R3
      Call un.StripQuotes
      Pop $R3
      ${ExitDo}
    ${EndIf}
  ${Loop}
  Push $R3
FunctionEnd
