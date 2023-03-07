import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.swabra
import jetbrains.buildServer.configs.kotlin.buildSteps.SSHUpload
import jetbrains.buildServer.configs.kotlin.buildSteps.powerShell
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.buildSteps.sshUpload
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

version = "2022.04"

project {
    description = "Build and publish VMPC2000XL binaries"

    vcsRoot(HttpsGithubComIzzyrealVmpcJuce)

    buildType(CompressUbuntuBinaries)
    buildType(Release)

    subProject(BuildInstallers)
    subProject(Vmpc2000xlDocumentation)
    subProject(BuildBinaries)
}

object CompressUbuntuBinaries : BuildType({
    name = "Compress Ubuntu binaries"
    description = "Publish Ubuntu binaries"

    enablePersonalBuilds = false
    artifactRules = """
        version.txt
        binaries/**/ubuntu/VMPC2000XL-Ubuntu20-x86_64-LV2.zip
        binaries/**/ubuntu/VMPC2000XL-Ubuntu20-x86_64-Standalone.zip
        binaries/**/ubuntu/VMPC2000XL-Ubuntu20-x86_64-VST3.zip
    """.trimIndent()
    maxRunningBuilds = 1

    steps {
        script {
            name = "Compress binaries"
            scriptContent = """
                VMPC_VERSION=${'$'}(cat version.txt)
                mkdir -p binaries/${'$'}{VMPC_VERSION}/ubuntu/
                
                chmod +x Standalone/VMPC2000XL
                
                cd Standalone
                zip -v ../binaries/${'$'}{VMPC_VERSION}/ubuntu/VMPC2000XL-Ubuntu20-x86_64-Standalone.zip ./VMPC2000XL
                
                cd ../VST3
                zip -v -r ../binaries/${'$'}{VMPC_VERSION}/ubuntu/VMPC2000XL-Ubuntu20-x86_64-VST3.zip ./VMPC2000XL.vst3
                
                cd ../LV2
                zip -v -r ../binaries/${'$'}{VMPC_VERSION}/ubuntu/VMPC2000XL-Ubuntu20-x86_64-LV2.zip ./VMPC2000XL.lv2
            """.trimIndent()
        }
    }

    triggers {
        finishBuildTrigger {
            buildType = "${BuildVmpc2000xlUbuntu.id}"
            successfulOnly = true
        }
    }

    features {
        swabra {
        }
    }

    dependencies {
        artifacts(BuildVmpc2000xlUbuntu) {
            buildRule = lastSuccessful()
            cleanDestination = true
            artifactRules = "binaries"
        }
    }

    requirements {
        equals("system.agent.name", "ubuntu-vm")
    }
})

object Release : BuildType({
    name = "Release"

    params {
        param("github-secret", "%vault:kv/gh!/token%")
    }

    steps {
        script {
            name = "Release"
            scriptContent = """
                version=${'$'}(cat version.txt)
                echo "Creating release for v${'$'}version"
                
                github-release release --security-token %github-secret% --user izzyreal --repo vmpc-juce --tag v${'$'}{version} --name "VMPC2000XL v${'$'}{version}" --description "https://github.com/izzyreal/mpc/blob/master/CHANGELOG.md"
                
                sleep 1
                
                github-release upload --security-token %github-secret% --user izzyreal --repo vmpc-juce --tag v${'$'}{version} --name VMPC2000XL-Installer-Intel-M1.pkg --file ${'$'}{version}/mac/VMPC2000XL-Installer-Intel-M1.pkg
                github-release upload --security-token %github-secret% --user izzyreal --repo vmpc-juce --tag v${'$'}{version} --name VMPC2000XL-Installer-Win7-x86_64.exe --file ${'$'}{version}/win/VMPC2000XL-Installer-Win7-x86_64.exe
                github-release upload --security-token %github-secret% --user izzyreal --repo vmpc-juce --tag v${'$'}{version} --name VMPC2000XL-Installer-Win10-x86_64.exe --file ${'$'}{version}/win/VMPC2000XL-Installer-Win10-x86_64.exe
                github-release upload --security-token %github-secret% --user izzyreal --repo vmpc-juce --tag v${'$'}{version} --name VMPC2000XL-Ubuntu20-x86_64-LV2.zip --file ${'$'}{version}/ubuntu/VMPC2000XL-Ubuntu20-x86_64-LV2.zip
                github-release upload --security-token %github-secret% --user izzyreal --repo vmpc-juce --tag v${'$'}{version} --name VMPC2000XL-Ubuntu20-x86_64-Standalone.zip --file ${'$'}{version}/ubuntu/VMPC2000XL-Ubuntu20-x86_64-Standalone.zip
                github-release upload --security-token %github-secret% --user izzyreal --repo vmpc-juce --tag v${'$'}{version} --name VMPC2000XL-Ubuntu20-x86_64-VST3.zip --file ${'$'}{version}/ubuntu/VMPC2000XL-Ubuntu20-x86_64-VST3.zip
            """.trimIndent()
        }
    }

    dependencies {
        artifacts(BuildWindows7Installer) {
            buildRule = lastSuccessful()
            cleanDestination = true
            artifactRules = "**/win/VMPC2000XL-Installer-Win7-x86_64.exe"
        }
        artifacts(BuildWindows10Installer) {
            buildRule = lastSuccessful()
            cleanDestination = true
            artifactRules = "**/win/VMPC2000XL-Installer-Win10-x86_64.exe"
        }
        artifacts(BuildMacOSInstaller) {
            buildRule = lastSuccessful()
            cleanDestination = true
            artifactRules = "**/mac/VMPC2000XL-Installer-Intel-M1.pkg =>"
        }
        artifacts(CompressUbuntuBinaries) {
            buildRule = lastSuccessful()
            cleanDestination = true
            artifactRules = """
                **/ubuntu/VMPC2000XL-Ubuntu20-x86_64-LV2.zip
                **/ubuntu/VMPC2000XL-Ubuntu20-x86_64-Standalone.zip
                **/ubuntu/VMPC2000XL-Ubuntu20-x86_64-VST3.zip
                version.txt
            """.trimIndent()
        }
    }

    requirements {
        equals("teamcity.agent.name", "Default Agent")
    }
})

object HttpsGithubComIzzyrealVmpcJuce : GitVcsRoot({
    name = "https://github.com/izzyreal/vmpc-juce"
    url = "https://github.com/izzyreal/vmpc-juce"
    branch = "refs/heads/master"
    branchSpec = "refs/heads/*"
})


object BuildBinaries : Project({
    name = "Build binaries"

    buildType(BuildMacOSBinaries)
    buildType(CodesignMacOSBinaries)
    buildType(BuildVmpc2000xlWindows7_32bit)
    buildType(BuildVmpc2000xlWindows7_64bit)
    buildType(BuildVmpc2000xlWindows10_32bit)
    buildType(BuildVmpc2000xlWindows10_64bit)
    buildType(BuildBinaries_BuildVmpc2000xlIOS)
    buildType(BuildVmpc2000xlUbuntu)
})

object BuildMacOSBinaries : BuildType({
    name = "Build VMPC2000XL MacOS binaries"
    description = "Build VMPC2000XL"

    artifactRules = """
        build/vmpc2000xl_artefacts/Release => binaries/macos
        -:build/vmpc2000xl_artefacts/Release/libVMPC2000XL_SharedCode.a
    """.trimIndent()
    publishArtifacts = PublishMode.SUCCESSFUL

    vcs {
        root(HttpsGithubComIzzyrealVmpcJuce)
    }

    steps {
        script {
            name = "CMake configure and build"
            scriptContent = """
                mkdir build && cd build
                cmake .. -G "Xcode" -DCMAKE_OSX_ARCHITECTURES="arm64;x86_64"
                
                xcodebuild -project vmpc2000xl.xcodeproj \
                -scheme vmpc2000xl_Standalone \
                -destination "generic/platform=macOS,name=Any Mac" \
                -configuration Release
                
                xcodebuild -project vmpc2000xl.xcodeproj \
                -scheme vmpc2000xl_AU \
                -destination "generic/platform=macOS,name=Any Mac" \
                -configuration Release
               
                xcodebuild -project vmpc2000xl.xcodeproj \
                -scheme vmpc2000xl_VST3 \
                -destination "generic/platform=macOS,name=Any Mac" \
                -configuration Release
               
                xcodebuild -project vmpc2000xl.xcodeproj \
                -scheme vmpc2000xl_LV2 \
                -destination "generic/platform=macOS,name=Any Mac" \
                -configuration Release
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            enabled = false
        }
    }

    features {
        swabra {
        }
    }

    requirements {
        exists("tools.xcode.platform.macosx")
    }
})

object BuildBinaries_BuildVmpc2000xlIOS : BuildType({
    name = "Build VMPC2000XL iOS"
    description = "Build VMPC2000XL standalone and AUv3"

    artifactRules = """
        build/vmpc2000xl_StandaloneAndAUv3.xcarchive => vmpc2000xl_StandaloneAndAUv3.xcarchive
    """.trimIndent()
    publishArtifacts = PublishMode.SUCCESSFUL

    vcs {
        root(HttpsGithubComIzzyrealVmpcJuce)
    }

    steps {
        script {
            name = "CMake configure and build"
            scriptContent = """
                mkdir build
                cmake \
                -Wno-dev \
                -B build \
                -G "Xcode" \
                -DCMAKE_BUILD_TYPE="Release" \
                -DCMAKE_SYSTEM_NAME=iOS \
                cd build
                xcodebuild -project vmpc2000xl.xcodeproj build -target vmpc2000xl_Standalone -parallelizeTargets -configuration Release -allowProvisioningUpdates
            """.trimIndent()
        }
        script {
            name = "xcodebuild AppStore archive"
            scriptContent = """
                cd build
                xcodebuild -project vmpc2000xl.xcodeproj \
                -scheme vmpc2000xl_Standalone \
                -allowProvisioningUpdates \
                -sdk iphoneos \
                -configuration Release \
                archive \
                -archivePath "./vmpc2000xl_StandaloneAndAUv3.xcarchive"
            """.trimIndent()
        }
        script {
            name = "xcodebuild publish to AppStore"
            scriptContent = """
                cd build
                xcodebuild -exportArchive \
                -archivePath ./vmpc2000xl_StandaloneAndAUv3.xcarchive \
                -exportOptionsPlist ../ExportOptions.plist \
                -exportPath "./" \
                -allowProvisioningUpdates
            """.trimIndent()
            enabled = true
        }
    }

    triggers {
        vcs {
            enabled = false
        }
    }

    features {
        swabra {
        }
    }

    requirements {
        equals("teamcity.agent.name", "Default Agent")
    }
})

object BuildVmpc2000xlUbuntu : BuildType({
    name = "Build VMPC2000XL Ubuntu"
    description = "Build VMPC2000XL Ubuntu binaries"

    artifactRules = """
        build/vmpc2000xl_artefacts/Release => binaries
        -:build/vmpc2000xl_artefacts/Release/libVMPC2000XL_SharedCode.a
        build/version.txt => binaries
    """.trimIndent()
    publishArtifacts = PublishMode.SUCCESSFUL

    vcs {
        root(HttpsGithubComIzzyrealVmpcJuce)
    }

    steps {
        script {
            scriptContent = """
                mkdir build && cd build
                cmake .. -G "Ninja Multi-Config" 
                ninja -f build-Release.ninja vmpc2000xl_All
            """.trimIndent()
        }
    }

    features {
        swabra {
        }
    }

    requirements {
        equals("teamcity.agent.jvm.os.name", "Linux")
    }
})

object BuildVmpc2000xlWindows10_32bit : BuildType({
    name = "Build VMPC2000XL Windows 10 32-bit"
    description = "Build VMPC2000XL"

    artifactRules = """
        build/vmpc2000xl_artefacts/Release => binaries/win32
        -:build/vmpc2000xl_artefacts/Release/VMPC2000XL_SharedCode.lib
    """.trimIndent()
    publishArtifacts = PublishMode.SUCCESSFUL

    vcs {
        root(HttpsGithubComIzzyrealVmpcJuce)
    }

    steps {
        script {
            name = "CMake configure and build"
            scriptContent = """
                mkdir build && cd build
                cmake .. -G "Visual Studio 16 2019" -A Win32
                cmake --build . --config Release --target vmpc2000xl_Standalone vmpc2000xl_VST3
            """.trimIndent()
        }
    }

    features {
        swabra {
        }
    }

    requirements {
        equals("teamcity.agent.jvm.os.name", "Windows 10")
    }
})

object BuildVmpc2000xlWindows7_32bit : BuildType({
    name = "Build VMPC2000XL Windows 7 32-bit"
    description = "Build VMPC2000XL"

    artifactRules = """
        build/vmpc2000xl_artefacts/Release => binaries/win32
        -:build/vmpc2000xl_artefacts/Release/VMPC2000XL_SharedCode.lib
    """.trimIndent()
    publishArtifacts = PublishMode.SUCCESSFUL

    vcs {
        root(HttpsGithubComIzzyrealVmpcJuce)
    }

    steps {
        script {
            name = "CMake configure and build"
            scriptContent = """
                mkdir build && cd build
                cmake .. -G "Visual Studio 16 2019" -A Win32 -DVMPC2000XL_WIN7=1
                cmake --build . --config Release --target vmpc2000xl_Standalone vmpc2000xl_VST3
            """.trimIndent()
        }
    }

    features {
        swabra {
        }
    }

    requirements {
        equals("teamcity.agent.jvm.os.name", "Windows 10")
    }
})

object BuildVmpc2000xlWindows10_64bit : BuildType({
    name = "Build VMPC2000XL Windows 10 64-bit"
    description = "Build VMPC2000XL"

    artifactRules = """
        build/vmpc2000xl_artefacts/Release => binaries/win64
        -:build/vmpc2000xl_artefacts/Release/VMPC2000XL_SharedCode.lib
    """.trimIndent()
    publishArtifacts = PublishMode.SUCCESSFUL

    vcs {
        root(HttpsGithubComIzzyrealVmpcJuce)
    }

    steps {
        script {
            name = "CMake configure and build"
            scriptContent = """
                mkdir build && cd build
                cmake .. -G "Visual Studio 16 2019"
                cmake --build . --config Release --target vmpc2000xl_Standalone vmpc2000xl_VST3
            """.trimIndent()
        }
    }

    features {
        swabra {
        }
    }

    requirements {
        equals("teamcity.agent.jvm.os.name", "Windows 10")
    }
})

object BuildVmpc2000xlWindows7_64bit : BuildType({
    name = "Build VMPC2000XL Windows 7 64-bit"
    description = "Build VMPC2000XL"

    artifactRules = """
        build/vmpc2000xl_artefacts/Release => binaries/win64
        -:build/vmpc2000xl_artefacts/Release/VMPC2000XL_SharedCode.lib
    """.trimIndent()
    publishArtifacts = PublishMode.SUCCESSFUL

    vcs {
        root(HttpsGithubComIzzyrealVmpcJuce)
    }

    steps {
        script {
            name = "CMake configure and build"
            scriptContent = """
                mkdir build && cd build
                cmake .. -G "Visual Studio 16 2019" -DVMPC2000XL_WIN7=1
                cmake --build . --config Release --target vmpc2000xl_Standalone vmpc2000xl_VST3
            """.trimIndent()
        }
    }

    features {
        swabra {
        }
    }

    requirements {
        equals("teamcity.agent.jvm.os.name", "Windows 10")
    }
})

object BuildInstallers : Project({
    name = "Build installers"

    vcsRoot(BuildInstallers_VmpcInstallerScripts)

    buildType(BuildMacOSInstaller)
    buildType(BuildWindows7Installer)
    buildType(BuildWindows10Installer)
})

object BuildWindows10Installer : BuildType({
    name = "Build Windows 10 installer"
    description = "Build Windows 10 32/64-bit installer"

    artifactRules = """installers\**\win\VMPC2000XL-Installer-Win10-x86_64.exe"""

    vcs {
        root(BuildInstallers_VmpcInstallerScripts)
    }

    steps {
        powerShell {
            name = "Build Windows 10 32/64-bit installer"
            scriptMode = script {
                content = """
                    ${'$'}env:VERSION_IN_EXECUTABLE = (Get-Command binaries\win64\Standalone\VMPC2000XL.exe).FileVersionInfo.FileVersion
                    
                    ${'$'}env:32_BIT_EXECUTABLE_PATH = '..\binaries\win32\Standalone\VMPC2000XL.exe'
                    ${'$'}env:32_BIT_VST3_PATH       = '..\binaries\win32\VST3\VMPC2000XL.vst3\*'
                    
                    ${'$'}env:64_BIT_EXECUTABLE_PATH = '..\binaries\win64\Standalone\VMPC2000XL.exe'
                    ${'$'}env:64_BIT_VST3_PATH       = '..\binaries\win64\VST3\VMPC2000XL.vst3\*'
                    
                    ${'$'}env:DEMO_DATA_PATH         = '..\demo_data\*'
                    ${'$'}env:INSTALLER_SCRIPT_PATH  = '%teamcity.build.workingDir%\win\vmpc.iss'
                    
                    ${'$'}env:OUTPUT_DIR             = '%teamcity.build.workingDir%\installers\' + ${'$'}env:VERSION_IN_EXECUTABLE + '\win'
                    
                    iscc ${'$'}env:INSTALLER_SCRIPT_PATH
                    mv ${'$'}env:OUTPUT_DIR\VMPC2000XL-Installer-x86_64.exe ${'$'}env:OUTPUT_DIR\VMPC2000XL-Installer-Win10-x86_64.exe
                """.trimIndent()
            }
        }
    }

    features {
        swabra {
        }
    }

    dependencies {
        artifacts(BuildVmpc2000xlWindows10_32bit) {
            buildRule = lastSuccessful()
            cleanDestination = true
            artifactRules = "binaries => binaries"
        }
        artifacts(BuildVmpc2000xlWindows10_64bit) {
            buildRule = lastSuccessful()
            cleanDestination = true
            artifactRules = "binaries => binaries"
        }
    }
})

object BuildWindows7Installer : BuildType({
    name = "Build Windows 7 installer"
    description = "Build Windows 7 32/64-bit installer"

    artifactRules = """installers\**\win\VMPC2000XL-Installer-Win7-x86_64.exe"""

    vcs {
        root(BuildInstallers_VmpcInstallerScripts)
    }

    steps {
        powerShell {
            name = "Build Windows 7 32/64-bit installer"
            scriptMode = script {
                content = """
                    ${'$'}env:VERSION_IN_EXECUTABLE = (Get-Command binaries\win64\Standalone\VMPC2000XL.exe).FileVersionInfo.FileVersion
                    
                    ${'$'}env:32_BIT_EXECUTABLE_PATH = '..\binaries\win32\Standalone\VMPC2000XL.exe'
                    ${'$'}env:32_BIT_VST3_PATH       = '..\binaries\win32\VST3\VMPC2000XL.vst3\*'
                    
                    ${'$'}env:64_BIT_EXECUTABLE_PATH = '..\binaries\win64\Standalone\VMPC2000XL.exe'
                    ${'$'}env:64_BIT_VST3_PATH       = '..\binaries\win64\VST3\VMPC2000XL.vst3\*'
                    
                    ${'$'}env:DEMO_DATA_PATH         = '..\demo_data\*'
                    ${'$'}env:INSTALLER_SCRIPT_PATH  = '%teamcity.build.workingDir%\win\vmpc.iss'
                    
                    ${'$'}env:OUTPUT_DIR             = '%teamcity.build.workingDir%\installers\' + ${'$'}env:VERSION_IN_EXECUTABLE + '\win'
                    
                    iscc ${'$'}env:INSTALLER_SCRIPT_PATH
                    mv ${'$'}env:OUTPUT_DIR\VMPC2000XL-Installer-x86_64.exe ${'$'}env:OUTPUT_DIR\VMPC2000XL-Installer-Win7-x86_64.exe
                """.trimIndent()
            }
        }
    }

    features {
        swabra {
        }
    }

    dependencies {
        artifacts(BuildVmpc2000xlWindows7_32bit) {
            buildRule = lastSuccessful()
            cleanDestination = true
            artifactRules = "binaries => binaries"
        }
        artifacts(BuildVmpc2000xlWindows7_64bit) {
            buildRule = lastSuccessful()
            cleanDestination = true
            artifactRules = "binaries => binaries"
        }
    }
})

object CodesignMacOSBinaries : BuildType({
    name = "CodesignMacOSBinaries"

    artifactRules = "binaries => binaries"
    publishArtifacts = PublishMode.SUCCESSFUL

    params {
        param("dev-identity-app", "%vault:kv/apple-id!/dev-identity-app%")
    }

    steps {
        script {
            name = "Codesign binaries"
            scriptContent = """
                codesign --force -s "%dev-identity-app%" \
                -v ./binaries/Standalone/VMPC2000XL.app \
                --deep --strict --options=runtime --timestamp
               
                codesign --force -s "%dev-identity-app%" \
                -v ./binaries/AU/VMPC2000XL.component \
                --deep --strict --options=runtime --timestamp
               
               codesign --force -s "%dev-identity-app%" \
                -v ./binaries/VST3/VMPC2000XL.vst3 \
                --deep --strict --options=runtime --timestamp
            """.trimIndent()
        }
    }

    features {
        swabra {
        }
    }

    dependencies {
        artifacts(BuildMacOSBinaries) {
            buildRule = lastSuccessful()
            artifactRules = "binaries/macos => binaries"
        }
    }

    requirements {
        equals("teamcity.agent.jvm.os.name", "Mac OS X")
    }
})

object BuildMacOSInstaller : BuildType({
    name = "Build macOS installer"

    artifactRules = "installers/**/mac/VMPC2000XL-Installer-Intel-M1.pkg"
    publishArtifacts = PublishMode.SUCCESSFUL

    params {
        param("env.version", "0")
        param("github-secret", "%vault:kv/gh!/token%")
        param("notarytool-password", "%vault:kv/notarytool!/password%")
        param("team-id", "%vault:kv/apple-id!/team-id%")
        param("dev-identity-installer", "%vault:kv/apple-id!/dev-identity-installer%")
    }

    vcs {
        root(BuildInstallers_VmpcInstallerScripts)

        checkoutMode = CheckoutMode.ON_AGENT
        cleanCheckout = true
    }

    steps {
        script {
            name = "Build installer"
            scriptContent = """
                export version=${'$'}(/usr/libexec/PlistBuddy -c 'print :CFBundleVersion' binaries/Standalone/VMPC2000XL.app/Contents/Info.plist)
                
                packagesutil set package-1 version ${'$'}version --file ./mac/VMPC2000XL.pkgproj
                packagesutil set package-2 version ${'$'}version --file ./mac/VMPC2000XL.pkgproj
                packagesutil set package-3 version ${'$'}version --file ./mac/VMPC2000XL.pkgproj
                
                sed -i '' "s#<string>VMPC2000XL .*</string>#<string>VMPC2000XL ${'$'}version</string>#g" ./mac/VMPC2000XL.pkgproj
                
                git commit -am "Bump Packages project versions to ${'$'}version"
                git push https://izzyreal:%github-secret%@github.com/izzyreal/vmpc-installer-scripts 
                
                mkdir -p installers/${'$'}{version}/mac
                
                chmod +x ./binaries/Standalone/VMPC2000XL.app/Contents/MacOS/VMPC2000XL
                               
                packagesbuild --build-folder %teamcity.build.workingDir%/installers/${'$'}{version}/mac ./mac/VMPC2000XL.pkgproj
                
                mv %teamcity.build.workingDir%/installers/${'$'}{version}/mac/VMPC2000XL-Installer-Intel-M1.pkg \
                %teamcity.build.workingDir%/installers/${'$'}{version}/mac/VMPC2000XL-Installer-Intel-M1-unsigned.pkg
                
                productsign --sign "%dev-identity-installer%" \
                %teamcity.build.workingDir%/installers/${'$'}{version}/mac/VMPC2000XL-Installer-Intel-M1-unsigned.pkg \
                %teamcity.build.workingDir%/installers/${'$'}{version}/mac/VMPC2000XL-Installer-Intel-M1.pkg
                
                xcrun notarytool submit %teamcity.build.workingDir%/installers/${'$'}{version}/mac/VMPC2000XL-Installer-Intel-M1.pkg \
                --apple-id izmaelverhage@gmail.com --password %notarytool-password% --team-id %team-id% --wait
                
                xcrun stapler staple %teamcity.build.workingDir%/installers/${'$'}{version}/mac/VMPC2000XL-Installer-Intel-M1.pkg
            """.trimIndent()
        }
    }

    features {
        swabra {
        }
    }

    dependencies {
        artifacts(CodesignMacOSBinaries) {
            buildRule = lastSuccessful()
            artifactRules = "binaries => binaries"
        }
    }

    requirements {
        equals("teamcity.agent.jvm.os.name", "Mac OS X")
    }
})

object BuildInstallers_VmpcInstallerScripts : GitVcsRoot({
    name = "vmpc-installer-scripts"
    url = "https://github.com/izzyreal/vmpc-installer-scripts"
    branch = "master"
    checkoutPolicy = AgentCheckoutPolicy.SHALLOW_CLONE
})


object Vmpc2000xlDocumentation : Project({
    name = "VMPC2000XL Documentation"

    vcsRoot(Vmpc2000xlDocumentation_HttpsGithubComIzzyrealVmpcDocs)

    buildType(Vmpc2000xlDocumentation_BuildAndPublishHtml)
})

object Vmpc2000xlDocumentation_BuildAndPublishHtml : BuildType({
    name = "Build and publish HTML and PDF"

    artifactRules = "_build => _build"

    params {
        param("sftp-user", "%vault:kv/izmarnl!/sftp-user%")
        param("sftp-password", "%vault:kv/izmarnl!/sftp-password%")
    }

    vcs {
        root(Vmpc2000xlDocumentation_HttpsGithubComIzzyrealVmpcDocs)
    }

    steps {
        script {
            name = "Build HTML"
            scriptContent = "sphinx-build . ./_build"
        }
        sshUpload {
            name = "Publish HTML"
            transportProtocol = SSHUpload.TransportProtocol.SFTP
            sourcePath = "_build/**"
            targetUrl = "sftp.izmar.nl:public/sites/vmpcdocs.izmar.nl"
            authMethod = password {
                username = "%sftp-user%"
                password = "%sftp-password%"
            }
        }
        script {
            name = "Build PDF"
            scriptContent = "sphinx-build -b rinoh . ./_build/rinoh"
        }
        sshUpload {
            name = "Publish PDF"
            transportProtocol = SSHUpload.TransportProtocol.SFTP
            sourcePath = "_build/rinoh/vmpc2000xl.pdf"
            targetUrl = "sftp.izmar.nl:public/sites/vmpcdocs.izmar.nl"
            authMethod = password {
                username = "%sftp-user%"
                password = "%sftp-password%"
            }
        }
    }

    triggers {
        vcs {
            branchFilter = "+:<default>"
        }
    }

    features {
        swabra {
        }
    }
    
    requirements {
        equals("teamcity.agent.name", "Default Agent")
    }
})

object Vmpc2000xlDocumentation_HttpsGithubComIzzyrealVmpcDocs : GitVcsRoot({
    name = "https://github.com/izzyreal/vmpc-docs#refs/heads/master"
    url = "https://github.com/izzyreal/vmpc-docs"
    branch = "refs/heads/master"
    branchSpec = "refs/heads/*"
})
