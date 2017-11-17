import React, { Component } from 'react';
import { requireNativeComponent, NativeModules, View, TouchableHighlight } from 'react-native';
import PropTypes from 'prop-types'

const ocrReaderModule = NativeModules['OcrReaderModule'];

const TEXT_READ = "text_read";
const LOW_STORAGE_EXCEPTION = "low_storage";
const NOT_YET_OPERATIONAL_EXCEPTION = "not_yet_operational";
const NO_PLAY_SERVICES_EXCEPTION = 'no_play_services';

class OcrReader extends Component {
  static propTypes = {
    onTextRead: PropTypes.func, // Callback that fires whenever a new ocr is read
    onException: PropTypes.func, // function(reason)

    focusMode: PropTypes.number, // int
    cameraFillMode: PropTypes.number, // int
    ...View.propTypes
  };

  constructor(props) {
    super(props)

    this._onChange = this._onChange.bind(this);
  }

  componentWillMount() {
    resumeReader()
      .then(() => {
        console.log("OcrReader was resumed on component mount.");
      })
      .catch(e => {
        console.log(e);
      });
  }

  componentWillUnmount() {
    pauseReader()
      .then(() => {
        console.log("OcrReader was paused on component mount.");
      })
      .catch(e => {
        console.log(e);
      });
  }

  _onChange(event: Event) {
    // Kirim data dari Native ke Javascript
    switch (event.nativeEvent.key) {
      case TEXT_READ:
        const onTextRead = this.props.onTextRead;
        if (onTextRead) {
          onTextRead({
            data: event.nativeEvent.data,
          });
        }
        break;
      case NOT_YET_OPERATIONAL_EXCEPTION:
      case LOW_STORAGE_EXCEPTION:
      case NO_PLAY_SERVICES_EXCEPTION:
        if (this.props.onException) this.props.onException(event.nativeEvent.key);
        break;
    }
  }

  render() {
    return (
      <NativeOcrReader
        {...this.props}
        onChange={this._onChange}
      />
    );
  }
}

// Ambil Native View Manager dan Connect ke OcrReader (Public class Javascript)
const NativeOcrReader = requireNativeComponent('RCTOcrReaderManager', OcrReader, {
  nativeOnly: {onChange: true}
});



/* --------------------------------------
 * ------------- Exports ----------------
 * --------------------------------------
 */

// Alternatives: AUTO, TAP, FIXED. Note: focusMode TAP won't work if you place a view on top of OcrReader, that catches all touch events.
export const FocusMode = ocrReaderModule.FocusMode;

// Alternatives: COVER, FIT
export const CameraFillMode = ocrReaderModule.CameraFillMode;

export const Exception = { LOW_STORAGE: LOW_STORAGE_EXCEPTION, NOT_OPERATIONAL: NOT_YET_OPERATIONAL_EXCEPTION, NO_PLAY_SERVICES: NO_PLAY_SERVICES_EXCEPTION };

// Mapping fungsi Native ke Javascript
export const pauseReader = ocrReaderModule.pause;
export const resumeReader = ocrReaderModule.resume;

export default OcrReader;
