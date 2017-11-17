## Installing it as a library in your main project
There are many ways to do this, here's the way I do it:

1. Push it to **GitHub**.
2. Do `npm install --save git+https://github.com/deehidayat/react-native-ocr-reader.git` in your main project.
3. Link the library:
    * Add the following to `android/settings.gradle`:
        ```
        include ':react-native-ocr-reader'
        project(':react-native-ocr-reader').projectDir = new File(settingsDir, '../node_modules/react-native-ocr-reader/android')
        ```

    * Add the following to `android/app/build.gradle`:
        ```xml
        ...

        dependencies {
            ...
            compile project(':react-native-ocr-reader')
        }
        ```
    * Add the following to `android/app/src/main/java/**/MainApplication.java`:
        ```java
        package com.motivation;

        import com.google.android.gms.samples.vision.ocrreader.OcrReaderPackage;  // add this for react-native-ocr-reader

        public class MainApplication extends Application implements ReactApplication {

            @Override
            protected List<ReactPackage> getPackages() {
                return Arrays.<ReactPackage>asList(
                    new MainReactPackage(),
                    new OcrReaderPackage()     // add this for react-native-ocr-reader
                );
            }
        }
        ```
4. Simply `import/require` it by the name defined in your library's `package.json`:

    ```javascript
    import OcrReader from 'react-native-ocr-reader'
    <OcrReader
        style={{flex: 1}}
        onTextRead={({data})=>{ console.log(data) }}
        />
    ```
5. You can test and develop your library by importing the `node_modules` library into **Android Studio** if you don't want to install it from _git_ all the time.