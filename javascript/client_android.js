var ctroffset = 0; //295
var connected = false;
var username;
var streammode = "stop";
var steeringmode;
var xmlhttp=null;

function loaded() {
	
}

function flashloaded() {
	openxmlhttp("rtmpPortRequest",rtmpPortReturned);
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
	    xmlhttp.send(null);
	  }
	  else {
	    alert("Your browser does not support XMLHTTP.");
	  }
}

function rtmpPortReturned() { //xmlhttp event handler
	if (xmlhttp.readyState==4) {// 4 = "loaded"
		if (xmlhttp.status==200) {// 200 = OK
			getFlashMovie("oculus_android").setRtmpPort(xmlhttp.responseText);
			var user = window.OCULUSANDROID.getUser();
			var pass = window.OCULUSANDROID.getPass();
			//setTimeout("loginsend("+user+","+pass+");", 1000);
			loginsend(user,pass);
		}
	}
}

function callServer(fn, str) {
	getFlashMovie("oculus_android").flashCallServer(fn,str);
}

function play(str) {
	streammode = str;
	var num = 1;
	if (streammode == "stop") { num =0 ; } 
	getFlashMovie("oculus_android").flashplay(num);
}

function getFlashMovie(movieName) {
	var isIE = navigator.appName.indexOf("Microsoft") != -1;
	return (isIE) ? window[movieName] : document[movieName];
}

function publish(str) {
	callServer("publish", str);
}

function message(message, colour, status, value) {
	if (status != null) {  //(!/\S/.test(d.value))
		if (status == "multiple") { setstatusmultiple(value); }
		else { setstatus(status, value); }
	}
	//window.OCULUSANDROID.message(message);
}

function setstatus(status, value) {
	if (value.toUpperCase() == "CONNECTED" && !connected) { // initialize
		callServer("statuscheck","");
		connected = true;
		window.OCULUSANDROID.message("Connected to Oculus");
		//publish("camera");
	}
	if (status == "stream" && (value.toUpperCase() != streammode.toUpperCase())) { 
		play(value); 
		window.OCULUSANDROID.message("stream: "+value);
	}
	if (status == "connection" && value == "closed") { window.OCULUSANDROID.connectionClosed(); }
	/*
	 * below unused for now
	 */
	if (status == "someonealreadydriving") { someonealreadydriving(value); }
	if (status == "user") { 
		username = value; 
		window.OCULUSANDROID.message(username+ " signed in");
		// callServer('speedset','fast');
	}
	if (status == "hijacked") { window.location.reload(); }
	if (status == "light") { window.OCULUSANDROID.lightPresent(); }
}

function setstatusmultiple(value) {
	var statusarray = new Array();
	statusarray = value.split(" ");
	for (var i = 0; i<statusarray.length; i+=2) {
		setstatus(statusarray[i], statusarray[i+1]);
	}
}

function loginsend(user, pass) {
	getFlashMovie("oculus_android").connect(user+" "+pass+" false"); // false is for cookie param, not applicable here
}

/*
 * ALL FUNCTIONS BELOW HERE MAY OR MAY NOT END UP BEING USED, FOR REF ONLY FOR NOW
 */

function motionenabletoggle() {
	callServer("motionenabletoggle", "");
}

function move(str) {
	callServer("move", str);
}

function nudge(direction) {
	callServer("nudge", direction);
}

function slide(direction) {
	callServer("slide", direction);
}

function speech() {
	var a = document.getElementById('speechbox');
	var str = a.value;
	a.value = "";
	callServer("speech", str);
}

function camera(fn) {
	callServer("cameracommand", fn);
}

function speedset(str) {
	callServer("speedset", str);
}

function dock(str) {
	callServer("dock", str);
}

function someonealreadydriving(value) {
	clearTimeout(logintimer);
	overlay("on");
	document.getElementById("overlaydefault").style.display = "none";
	document.getElementById("someonealreadydrivingbox").style.display = "";
	document.getElementById("usernamealreadydrivingbox").innerHTML = value.toUpperCase();
}

function beapassenger() {
	callServer("beapassenger", username);
	overlay("off");
	setstatus("connection","PASSENGER");
}

function assumecontrol() {
	callServer("assumecontrol", username);
}

function playerexit() {
	callServer("playerexit","");
}

function steeringmousedown(id) {
	document.getElementById(id).style.backgroundColor = "#45F239";
	setTimeout("document.getElementById('"+id+"').style.backgroundColor='transparent';",200);
	/*
	if (steeringmode == id) {
		move("stop");
		steeringmode="stop";
		id = null;
	}
	*/
	if (id == "forward") { move("forward"); }
	if (id == "backward") { move("backward"); }
	if (id == "rotate right") { move("right"); }
	if (id == "rotate left") { move("left"); }
	if (id == "slide right") { slide("right"); }
	if (id == "slide left") { slide("left"); }
	if (id == "nudge right") { nudge("right"); id = null; }
	if (id == "nudge left") { nudge("left"); id = null; }
	if (id == "nudge forward") { nudge("forward"); }
	if (id == "nudge backward") { nudge("backward"); }
	if (id == "stop") { move("stop"); }
	if (id == "camera up") { camera("upabit"); id=null; }
	if (id == "camera down") { camera("downabit"); id=null; }
	if (id == "camera horizontal") { camera("horiz"); id=null; }
	if (id == "speed slow") { speedset("slow"); id=null; }
	if (id == "speed medium") { speedset("med"); id=null; }
	if (id == "speed fast") { speedset("fast"); id=null; }
	if (id == "menu") { move("stop"); menu(); id=null; }
	if (id) {
		steeringmode = id;
	}
}

