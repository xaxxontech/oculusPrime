'use strict';

// Set up media stream constant and parameters.

// In this codelab, you will be streaming video only: "video: true".
// Audio will not be streamed because it is set to "audio: false" by default.
const mediaStreamConstraints = {
  video: true,
};

// Set up to exchange only video.
const offerOptions = {
  offerToReceiveVideo: 1,
};

// Define initial start time of the call (defined as connection between peers).
let startTime = null;

// Define peer connections, streams and video elements.
const localVideo = document.getElementById('localVideo');
const remoteVideo = document.getElementById('remoteVideo');

let localStream;
let remoteStream;
let peerConnection = null;
let isClient = false;
let msgPollTimeout = null;
const msgPollInterval = 500;
let remoteDescrip = false;
let localDescrip = false;
let connectionChange = false;


// Define MediaStreams callbacks.

// Sets the MediaStream as the video element src.
function gotLocalMediaStream(mediaStream) {
	localVideo.srcObject = mediaStream;
	localStream = mediaStream;
	trace('Received local stream.');
	// callButton.disabled = false;  // Enable call button.
  
	// Get local media stream tracks.
	const videoTracks = localStream.getVideoTracks();
	const audioTracks = localStream.getAudioTracks();
	if (videoTracks.length > 0) 
		trace(`Using video device: ${videoTracks[0].label}.`);

	if (audioTracks.length > 0) 
		trace(`Using audio device: ${audioTracks[0].label}.`);
		
	peerConnection.addTrack(videoTracks[0]);
}

// Handles error by logging a message to the console.
function handleLocalMediaStreamError(error) {
  trace(`navigator.getUserMedia error: ${error.toString()}.`);
}

// Handles remote MediaStream success by adding it as the remoteVideo src.
function gotRemoteMediaStream(event) {
  const mediaStream = event.stream;
  remoteVideo.srcObject = mediaStream;
  // remoteStream = mediaStream;
  // setTimeout("remoteVideo.srcObject = remoteStream", 2000);
  trace('received remote stream.');
	
}


// Add behavior for video streams.

// Logs a message with the id and size of a video element.
function logVideoLoaded(event) {
  const video = event.target;
  trace(`${video.id} videoWidth: ${video.videoWidth}px, ` +
        `videoHeight: ${video.videoHeight}px.`);
}

// Logs a message with the id and size of a video element.
// This event is fired when video begins streaming.
function logResizedVideo(event) {
  logVideoLoaded(event);

  if (startTime) {
    const elapsedTime = window.performance.now() - startTime;
    startTime = null;
    trace(`Setup time: ${elapsedTime.toFixed(3)}ms.`);
  }
}

if (localVideo)
	localVideo.addEventListener('loadedmetadata', logVideoLoaded);
if (remoteVideo) {
	remoteVideo.addEventListener('loadedmetadata', logVideoLoaded);
	remoteVideo.addEventListener('onresize', logResizedVideo);
}


// Define RTC peer connection behavior.

// Connects with new peer candidate.
function handleConnection(event) {
	
	if (!event.candidate) return;
  
	let msg = JSON.stringify({ candidate: event.candidate });

	if (isClient)
		postxmlhttp("/oculusPrime/webRTCServlet?msgfromclient="+msg, null, msg);
	else
		postxmlhttp("/oculusPrime/webRTCServlet?msgfromserver="+msg, null, msg);

}

// Logs that the connection succeeded.
function handleConnectionSuccess(peerConnection) {
  trace("addIceCandidate success.");
};

// Logs that the connection failed.
function handleConnectionFailure(peerConnection, error) {
  trace("failed to add ICE Candidate:\n ${error.toString()}.");
}

// Logs changes to the connection state.
function handleConnectionChange(event) {
	const pc = event.target;
	console.log('ICE state change event: ', event);
	trace(`ICE state: ${pc.iceConnectionState}.`);

	connectionChange = true;
	
	// if (pc.iceConnectionState == "connected" && !isClient)
		// peerConnection.addStream(localStream);
}

// Logs error when setting session description fails.
function setSessionDescriptionError(error) {
  trace(`Failed to create session description: ${error.toString()}.`);
}

// Logs success when setting session description.
function setDescriptionSuccess(peerConnection, functionName) {
  trace(`${functionName} complete.`);
}

// Logs success when localDescription is set.
function setLocalDescriptionSuccess(peerConnection) {
  setDescriptionSuccess(peerConnection, 'setLocalDescription');
  localDescrip = true;
}

// Logs success when remoteDescription is set.
function setRemoteDescriptionSuccess(peerConnection) {
  setDescriptionSuccess(peerConnection, 'setRemoteDescription');
  remoteDescrip = true;
}

// sets local description and sends offer to signalling server (client)
function createdOffer(description) {
	
	trace('sending offer.');
	let msg = JSON.stringify({desc: description});
	postxmlhttp("/oculusPrime/webRTCServlet?msgfromclient="+msg, null, msg);
	
	trace('peerConnection setLocalDescription start.');
	setLocalDescription(description);

}

// function requestAnswer() {
	// openxmlhttp("/oculusPrime/webRTCServlet?requestanswer", requestAnswerResponse);
// }

// Logs answer to offer creation and sets peer connection session descriptions.
function createdAnswer(description) {
	trace('sending answer.');
	let msg = JSON.stringify({desc: description});
	postxmlhttp("/oculusPrime/webRTCServlet?msgfromserver="+msg, null, msg);

	trace('peerConnection setLocalDescription start.');
	setLocalDescription(description);
}

function setRemoteDescription(description) {
	trace('peerConnection setRemoteDescription start.');
	peerConnection.setRemoteDescription(new RTCSessionDescription(description))
		.then(() => {
		setRemoteDescriptionSuccess(peerConnection);
		}).catch(setSessionDescriptionError);
}

function setLocalDescription(description) {
	trace('localPeerConnection setLocalDescription start.');
	peerConnection.setLocalDescription(description)
		.then(() => {
		setLocalDescriptionSuccess(peerConnection);
		}).catch(setSessionDescriptionError);
}

// Define and add behavior to buttons.

// Define action buttons.
const startButton = document.getElementById('startButton');
const callButton = document.getElementById('callButton');
const hangupButton = document.getElementById('hangupButton');

// Set up initial action buttons status: disable call and hangup.
if (callButton) callButton.disabled = false;
if (hangupButton) hangupButton.disabled = true;


// Handles start button action: creates local MediaStream (server)
function startAction() {
	openxmlhttp("/oculusPrime/webRTCServlet?clearvars", null);
	msgPollTimeout = setTimeout("checkForMsg();", msgPollInterval);

	navigator.mediaDevices.getUserMedia(mediaStreamConstraints)
		.then(gotLocalMediaStream).catch(handleLocalMediaStreamError);
	trace('Requesting local stream.');

}

// CLIENT START
function callAction() {
	callButton.disabled = true;
	hangupButton.disabled = false;
	
	isClient = true;
	msgPollTimeout = setTimeout("checkForMsg();", msgPollInterval);

	trace('Starting call.');
	startTime = window.performance.now();
	
	// createPeerConnection();

	peerConnection.addEventListener('addstream', gotRemoteMediaStream);
	
	trace('peerConnection createOffer start.');
	peerConnection.createOffer(offerOptions) //offerOptions
		.then(createdOffer).catch(setSessionDescriptionError);
}

function createPeerConnection() {
	// Create peer connections and add behavior.
	const servers = null;  // Allows for RTC server configuration.

	peerConnection = new RTCPeerConnection(servers);
	trace('Created peer connection object peerConnection.');

	peerConnection.addEventListener('iceconnectionstatechange', handleConnectionChange);
	peerConnection.addEventListener('icecandidate', handleConnection);

	peerConnection.onnegotiationneeded = function () {
		trace("NEGOTATION NEEDED");
	};
}

// Handles hangup action: ends up call, closes connections and resets peers.
function hangupAction() {
  peerConnection.close();
  peerConnection = null;
  hangupButton.disabled = true;
  callButton.disabled = false;
  trace('Ending call.');
  
  localDescrip = remoteDescrip = false;
}

// Add click event handlers for buttons.
if (startButton)
	startButton.addEventListener('click', startAction);
if (callButton) callButton.addEventListener('click', callAction);
if (hangupButton) hangupButton.addEventListener('click', hangupAction);


// Logs an action (text) and the time when it happened on the console.
function trace(text) {
	text = text.trim();
	if (isClient) text = "CLIENT: "+text;
	else text = "SERVER: "+text;
	const now = (window.performance.now() / 1000).toFixed(3);
	console.log(now, text);
}


let xmlhttp=null;

function postxmlhttp(theurl, functionname, data) {
	if (window.XMLHttpRequest) {// code for all new browsers
		xmlhttp=new XMLHttpRequest();}
	else if (window.ActiveXObject) {// code for IE5 and IE6
		xmlhttp=new ActiveXObject("Microsoft.XMLHTTP"); 
		theurl += "?" + new Date().getTime();
	}
	if (xmlhttp!=null) {
		xmlhttp.onreadystatechange=functionname; // event handler function call;
		xmlhttp.open("POST",theurl,true);
		xmlhttp.send(data);
	}
	else {
		alert("Your browser does not support XMLHTTP.");
	}
}

function openxmlhttp(theurl, functionname) {
	  if (window.XMLHttpRequest) {// code for all new browsers
	    xmlhttp=new XMLHttpRequest();}
	  else if (window.ActiveXObject) {// code for IE5 and IE6
	    xmlhttp=new ActiveXObject("Microsoft.XMLHTTP"); 
	    theurl += "?" + new Date().getTime();
	  }
	  if (xmlhttp!=null) {
	    xmlhttp.onreadystatechange=functionname; // event handler function call;
	    xmlhttp.open("GET",theurl,true);
	    xmlhttp.send();
	  }
	  else {
	    alert("Your browser does not support XMLHTTP.");
	  }
}

function checkForMsg() {
	if (isClient)
		openxmlhttp("/oculusPrime/webRTCServlet?requestservermsg", msgReceived);
	else 
		openxmlhttp("/oculusPrime/webRTCServlet?requestclientmsg", msgReceived);
	
	clearTimeout(msgPollTimeout);
	msgPollTimeout = setTimeout("checkForMsg();", msgPollInterval);
}

function msgResponse() {
		if (xmlhttp.readyState==4) {// 4 = "loaded"
		if (xmlhttp.status==200) {// 200 = OK
			if (xmlhttp.responseText == "ok") { 
				trace("msgRcvd");
			}
		}
	}
}

function msgReceived() { 
	if (xmlhttp.readyState==4) {// 4 = "loaded"
		if (xmlhttp.status==200) {// 200 = OK
			
			if (xmlhttp.responseText == "") { // no msg
				return;
			}
			
			// msg recevied
			
			trace("webRTCServletResponse:\n"+xmlhttp.responseText);
			
			let msg = JSON.parse(xmlhttp.responseText);

			if (msg.desc) {
				let description = new RTCSessionDescription(msg.desc);

				if (description.type == "offer") { 
					// createPeerConnection();

					// Add local stream to connection 
					trace('Added local stream to peerConnection.');						
					// peerConnection.addStream(localStream);

					setRemoteDescription(msg.desc);

					// create answer
					trace('peerConnection createAnswer start.');
					peerConnection.createAnswer()
						.then(createdAnswer)
						.catch(setSessionDescriptionError);
						
				}
				else if (description.type == "answer") {
					setRemoteDescription(msg.desc); 				
				}
			
			}
			else {
				if (msg.candidate) {
					addIceCandidate(msg.candidate); 
				}
			}
			
			clearTimeout(msgPollTimeout);
			msgPollTimeout = setTimeout("checkForMsg();", 50);
			// checkForMsg();

		}
	}
}

function addIceCandidate(candidatemsg) {
	// trace(peerConnection.connectionState);
	
	if (!localDescrip || !remoteDescrip || peerConnection==null) { // || !connectionChange) { // || peerConnection==null) {  // peerConnection.connectionState != "connected" || || !localDescrip  || !remoteDescrip
		setTimeout("c = JSON.parse('"+JSON.stringify(candidatemsg)+"'); addIceCandidate(c);", 500);
		trace("CANDIDATE DELAYED: "+JSON.stringify(candidatemsg));
		return;
	}

	trace(`${peerConnection} ICE candidate:\n` +
	`${JSON.stringify(candidatemsg)}.`);

	peerConnection.addIceCandidate(new RTCIceCandidate(candidatemsg)).then(() => {
		handleConnectionSuccess(peerConnection);
		}).catch((error) => {
		handleConnectionFailure(peerConnection, error); });
}

createPeerConnection();




