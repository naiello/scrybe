const fs = require('fs');
const Speech = require('@google-cloud/speech');

const speech = Speech();
const record = require('node-record-lpcm16');
//encoding and sample rate for request info
const encoding = 'LINEAR16';
const sampleRate = 16000;

const request = {
  config: {
    encoding : encoding,
    sampleRateHertz : sampleRate,
    languageCode : 'en'
  }
};

const recognizeStream = speech.createRecognizeStream(request)
  .on('error', console.error)
  .on('data', (data) => process.stdout.write(data.results));

//start the recording and send to the API
record.start({
  sampleRate : sampleRate,
  threshold : 0
}).pipe(recognizeStream);

console.log("Listening press ctrl+C to stop");
