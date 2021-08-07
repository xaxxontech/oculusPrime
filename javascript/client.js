//TODO: clean out unused 'overlay off' and 'extended settings box' references (in html too) 

var sentcmdcolor = "#777777";
var enablekeyboard = false;
var lagtimer = 0;
var officiallagtimer = 0;
var statusflashingarray= new Array();
var pinginterval = null; //timer
var pingcountdown = null; //timer
var pingcountdownaftercheck; //timer
var laststatuscheck = null;
var pingintervalamount = 5;
var pingtimetoworry = 5;
var battcheck = 0;
var battcheckinterval = 15;
var statuscheckreceived = false;
var missedstatuschecks = 0;
var cspulsatenum = 20;
var cspulsatenumdir = 1;
var cspulsate=false;
var cspulsatetimer = null; //timer
var ctroffset = 0; 
var ctroffsettemp;
var docklinewidth=60;
var videooverlaymouseposinterval = null; // timer
var videomouseaction = false;
var connected = false;
var logintimeout = 5000; 
var logintimer; // timer
var username;
var streammode = "stop";
var streamdetails = [];
var steeringmode;
var cameramoving = false;
var broadcasting = null;
var broadcastmicon = false; 
var clicksteeron = true;
var maxmessagecontents = 50000; // was 150000
var maxmessageboxsize = 4000;
var relay = false;
var webrtcinit = false;


var tempimage = new Image();
tempimage.src = 'images/eye.gif';
var tempimage2 = new Image();
tempimage2.src = 'images/ajax-loader.gif';
var tempimage3 = new Image();
tempimage3.src = 'images/dockline.gif';
var tempimage4 = new Image();
tempimage4.src = 'images/steering_icon_selected.gif';

var admin = false;
var userlistcalledby;
var initialdockedmessage = false;
var html5 = true;
var autodocking = false;
var sendcommandtimelast = 0;
var lastcommandsent;
var popupmenu_xoffset = null;
var popupmenu_yoffset = null;
var popupmenu_xsizestart;
var popupmenu_ysizestart;
var popupmenu_widthstart;
var popupmenu_heightstart;
var mainmenuwidth = null;
var bworig;
var rovvolume = 0;
var xmlhttp=null;
var spotlightlevel = -1;
var floodlightlevel = -1;
var videoscale = 100;
var pingcountertimer;
var pushtotalk;
var lastcommand; 
var maintopbarTimer = null;
var subwindows = ["aux", "context", "menu", "main", "rosmap", "radar"];  // purposely skipped "error" window
var windowpos = [null, null, null, null]; // needs same length as above
var oculusPrimeplayerSWF = null;
var recordmode = streammode;
var firstresize = true;


function loaded() {

	loadwindowpositions();
	loadrosmapwindowpos();
	
	
	if (clicksteeron) { clicksteer("on"); }
    overlay("on");
    browserwindowresized();
	
	commClientLoaded(); 
	if (/auth=/.test(document.cookie)) { 
		commLoginFromCookie(); 
		logintimer = setTimeout("eraseCookie('auth'); window.location.reload()", logintimeout);
	}
	else login(); // user input goes out thru commLogin() below
	videologo("on");
}

function main_window_resize() {
	var mm = document.getElementById("main_menu_over");
	var mt = document.getElementById("maintable");
	var x = mt.offsetLeft;
	var y = mt.offsetTop - mm.style.paddingTop;

	if (firstresize) {
		firstresize = false;
		var i = subwindows.indexOf("main");
		if (windowpos[i] != null) {
			x = windowpos[i][0];
			y = windowpos[i][1]+22;
		}
		else { // centered default
			x += document.body.clientWidth/2 - mm.offsetWidth/2; 
			if (x<4) x=4;
		}

		mm.style.left = x + "px";
		mm.style.top = y -22 + "px"; 
	}

	var under = document.getElementById("main_menu_under");
	under.style.display = "";
	under.style.width = null; //ie fix
	under.style.height = null; //ie fix
	var margin = 4;
	under.style.left = (mm.offsetLeft - margin) + "px";
	under.style.top = (mm.offsetTop -margin) + "px";
	under.style.width = (mm.offsetWidth + margin*2) + "px";
	under.style.height = (mm.offsetHeight + margin*2) + "px";
	under.style.display = "none";
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
			oculusPrimeplayerSWF.setRtmpPort(xmlhttp.responseText);
			if (/auth=/.test(document.cookie)) { loginfromcookie(); }
			else { login(); }
		}
	}
}

function resized() {
	var bw = document.body.clientWidth;
	if (bw < bworig) {
		document.body.style.paddingLeft = (bworig-bw)+"px";
	}
	else { document.body.style.paddingLeft = "0px"; }
	docklineposition();
	videooverlayposition();
	overlay("");
	videologo("");
}

function browserwindowresized() {
	docklineposition();
	videooverlayposition();
	overlay("");
	videologo("");	
	bworig= document.body.clientWidth;
}

function countdowntostatuscheck() {
	pinginterval = null;
	pingcountdown = setTimeout("statuscheck();",50);
}

function statuscheck() { 
	if (connected) {
		pingcountdown = null;
		str = "";
		battcheck += pingintervalamount;
		if (battcheck >= battcheckinterval) {
			battcheck = 0;
			str = "battcheck";
		}
		callServer("statuscheck",str); 
		var i = new Date().getTime();
		laststatuscheck = i; 
		officiallagtimer = i;
		pinginterval = setTimeout("countdowntostatuscheck()", pingintervalamount*1000);
		statuscheckreceived=false;
		setTimeout("checkforstatusreceived();",pingtimetoworry*1000);
		pingcountdownaftercheck = setTimeout("pingcountdownaftercheck = null", 10);
	}
}

function pingcounter() {
	clearTimeout(pingcountertimer);
	var pm = document.getElementById("pingcountmeter");
	var length;
	length = (pm.innerHTML).length; 
	if (length < 1) { length = pingintervalamount-2; }
	else  { length = length-1; }
	var str = "";
	for (var i = 0 ; i < length; i++) {
		str += "&middot;";
	} 
	document.getElementById("pingcountmeter").innerHTML = str;
	if (length>0) { pingcountertimer = setTimeout("pingcounter();",1000); }
}

function checkforstatusreceived() {
	if (statuscheckreceived==false) {
		missedstatuschecks += 1;
		clearTimeout(pinginterval);
		clearTimeout(pingcountdown);
		message("status request failed", sentcmdcolor);
		setstatus("lag","<span style='color: red;'>LARGE</span>");
		if (missedstatuschecks > 20) {
			connectionlost();
		}
		else { countdowntostatuscheck(); }
	}
	else { 
		missedstatuschecks =0;
	}
}

function connectionlost() {
	setstatus("connection","<span style='color: red;'>CLOSED</span>");
	document.title = "closed";
	connected = false;
	setstatusunknown();
	videologo("on");
	message("reload page", "green");
	clearTimeout(pinginterval);
	clearTimeout(pingcountdown);
}

function callServer(fn, str) {
	callServerComm(fn, str);
	sendcommandtimelast = new Date().getTime();
	lastcommandsent = fn+str;
}

function play(str) { // called by javascript only?
	streammode = str;
	var num = 1;
	var s = str.split("_");
	if (s[1]=="2") { num = 2; streammode = s[0] } // separate audio stream with avconv
}

function getFlashMovie(movieName) {
	var isIE = navigator.appName.indexOf("Microsoft") != -1;
	return (isIE) ? window[movieName] : document[movieName];
	// return document.getElementById(movieName);
}

function publish(str) { 
	if (str=="broadcast_mic") {  // forces page reload
		callServer("playerbroadcast","mic"); 
//		broadcasting = "mic";
//		message ("sending: playerbroadcast miconly",sentcmdcolor);
//		clicksteer("off");
	}
	else if (str=="broadcast_off") { // forces page reload
		callServer("playerbroadcast","off"); 
//		broadcasting = null;
//		message ("sending: playerbroadcast off",sentcmdcolor);
//		clicksteer("on"); 
	}
	else {
		message("sending command: publish " + str, sentcmdcolor);
		callServer("publish", str);
		lagtimer = new Date().getTime();
	}
}

function message(message, colour, status, value) {
	
	if (message != null) {
		var tempmessage = message;
		var d = new Date();
		
		if (message == "serverok") { 
			statuscheckreceived=true;
			if (officiallagtimer != 0) {
				var i = d.getTime() - officiallagtimer;
				setstatus("lag",i+"ms");
				//pingcounter();
				pingcountertimer = setTimeout("pingcounter();",1000);
			}
		}
		else {
		
			message = "<span style='color: #444444'>:</span><span style='color: "
					+ colour + "';>" + message + "</span>";
			messageboxping = "";
			hiddenmessageboxping = "";
			if (lagtimer != 0 && colour != sentcmdcolor) {
				var n = d.getTime() - lagtimer;
				messageboxping = " <span style='color: "+sentcmdcolor+"; font-size: 11px'>" + n + "ms</span>";
				hiddenmessageboxping = " <span style='color: orange'>" + n + "ms</span>";
			} 
			var a = document.getElementById('messagebox');
			var str = a.innerHTML;
			if (str.length > maxmessageboxsize) {
				str = str.slice(0, maxmessageboxsize);
				document.getElementById('messagemorelink').style.display = "";
			}
			else { document.getElementById('messagemorelink').style.display = "none"; }
			if (colour != sentcmdcolor) { 
				a.innerHTML = message + messageboxping + "<br>" + str; 
			}

			var b = document.getElementById('hiddenmessagebox');
			str = b.innerHTML;
			if (str.length > maxmessagecontents) {
				str = str.slice(0, maxmessagecontents);
			}
			var datetime = "<span style='color: #444444; font-size: 11px'>";
			var minutes = d.getMinutes();
			if (minutes < 10) { minutes = "0"+minutes; }
			var seconds = d.getSeconds();
			if (seconds < 10) { seconds = "0"+seconds; }
			datetime += d.getDate()+"-"+(d.getMonth()+1)+"-"+d.getFullYear();
			datetime += " "+d.getHours()+":"+minutes+":"+seconds;
			datetime +="</span>";
			b.innerHTML = message + hiddenmessageboxping + " &nbsp; " + datetime + "<br>" + str + " ";
		}
		// if (tempmessage == "playing stream") { videologo("off"); } 
		// if (tempmessage == "stream stopped") { videologo("on"); docklinetoggle("off"); }
		lagtimer = 0;
	}
	if (status != null) {  //(!/\S/.test(d.value))
		if (status == "multiple") { setstatusmultiple(value); }
		else { setstatus(status, value); }
	}
	
	if (typeof ws_server !== 'undefined') {
		if (!webrtcinit && ws_server && ws_port && turnserver_login && turnserver_port) {
			webrtcinit = true;
			websocketServerConnect(); // webrtc.js
		}
	}
}

function setstatus(status, value) {
	var a;
	if (a= document.getElementById(status+"_status")) {
		if (status=="dock" && value == "docking") { 
			value += " <img src='images/ajax-loader.gif' style='vertical-align: bottom;'>";
		}
		if (status=="cameratilt") { value += "&deg;"; }
		if (status=="stream") {
			var s = value.split("_");
			if (s[0] != streammode) { play(value); }
			value = s[0];
			
			if (value == "stop" || value == "mic") { videologo("on"); } 
			// else if (value == "camera" || value == "camandmic") videologo("off");
		}

		a.innerHTML = value;

		if (status == "connection" && value == "closed") { 
			connectionlost();
		}
		var clr = a.style.color;
		var bclr = a.style.backgroundColor;
		b=document.getElementById(status+"_title");
		var tclr = b.style.color;
		var tbclr = "#000000";
		if (statusflashingarray[status]==null) {
			statusflashingarray[status]="flashing";
			a.style.color = bclr;
			a.style.backgroundColor = clr;
			b.style.color = "#ffffff";
			b.style.backgroundColor = tbclr;
			var str1 = "document.getElementById('"+status+"_status').style.color='"+clr+"'; ";
			str1 += "document.getElementById('"+status+"_status').style.backgroundColor='"+bclr+"';";
			str1 += "document.getElementById('"+status+"_title').style.backgroundColor='"+bclr+"';";
			var str2 = "document.getElementById('"+status+"_status').style.color='"+bclr+"'; ";
			str2 += "document.getElementById('"+status+"_status').style.backgroundColor='"+clr+"';";
			str2 += "document.getElementById('"+status+"_title').style.color='"+tclr+"';";
			setTimeout(str1, 100);
			setTimeout(str2, 150);
			setTimeout(str1+"statusflashingarray['"+status+"']=null;", 250);
		}
	}
	if (status=="vidctroffset") { ctroffset = parseInt(value); }
	else if ((status=="connection" && value == "connected") && !connected) { // initialize
		overlay("off");
		countdowntostatuscheck(); 
		connected = true;
		keyboard("enable");
		setTimeout("videomouseaction = true;",30); // firefox screen glitch fix
		clearTimeout(logintimer);
		if (value == "relay") { relay = true; }
	}
	else if (status == "storecookie") {
		createCookie("auth",value,30); 
	}
	else if (status == "someonealreadydriving") { someonealreadydriving(value); }
	else if (status == "user") { username = value; }
	else if (status == "hijacked") { window.location.reload(); }
	else if (status == "admin" && value == "true") { admin = true; }
	else if (status == "dock") {
		if (initialdockedmessage==false) {
			initialdockedmessage = true;
			if (value == "docked") {
				message("docked","green");
			}
		}
		if (!/docking/i.test(value)) {
			docklinetoggle("off");
		}
	}
	else if (status == "streamsettings") {
		streamdetails = value.split("_");
	}
	else if (status=="autodocklock") { autodocklock(value)}
	else if (status=="autodockcancelled") { autodocking=false; autodock("cancel"); }
	else if (status=="softwareupdate") {
		if (value=="downloadcomplete") { softwareupdate("downloadcomplete",""); }
		else { softwareupdate("available",value); }
	}
	else if (status == "rovvolume") { rovvolume = parseInt(value); }
	else if (status == "light") { spotlightlevel = parseInt(value); }
	else if (status == "floodlight") { floodlightlevel = parseInt(value); }
	else if (status == "videoscale") { 
		var vs = parseInt(value);	
		if (vs != videoscale && (streammode == "camera" || streammode == "camandmic")){
			videoscale = vs;
			play(streammode);
		}
		else { videoscale = vs; }
	}
	else if (status == "developer") { 
		document.getElementById("developermenu").style.display = "";
	}
	else if (status == "navigation"  && relay != true) {
		document.getElementById("navigationmenu").style.display = "";
		navsystemstatustext = str;
	}
	else if (status == "debug") { debug(value); }
	else if (status=="loadpage") {
		playerexit();
		window.open(value,'_self');
	}
	else if (status=="selfstream") {
		if (value == "mic") {
			broadcasting = "mic";
			clicksteer("off");
		}
		else {
			broadcasting = "off";
			clicksteer("on");
		}
		document.getElementById("selfstream_controls").style.display = "";
		document.getElementById("self_stream_status").style.display = "";
	}
	else if (status=="record") {
		recordmode = value;
		a= document.getElementById("stream_status");
		var b = document.getElementById("recordlink");
		if (value == streammode && value != "stop") {
			a.innerHTML = "<span style='color: red'>&bull; "+value+"</span>";
			b.innerHTML = "stop recording";
			b.href="javascript: callServer('record','false');";
		}
		else if (value != streammode) {
			a.innerHTML = streammode;
			b.innerHTML = "record";
			b.href="javascript: callServer('record','true');";
		}
	}
	else if (status=="videowidth" && oculusPrimeplayerSWF == null) {
		document.getElementById("video").style.width = value+"px";
	}
	else if (status=="videoheight" && oculusPrimeplayerSWF == null) {
		document.getElementById("video").style.height = value+"px";
		browserwindowresized();
		main_window_resize();
	}
	else if (status=="webrtcserver") { ws_server = value; } // webrtc.js
	else if (status=="webrtcport") { ws_port = value; } // webrtc.js
	else if (status=="turnserverlogin") {turnserver_login = value; } // webrtc.js
	else if (status=="turnserverport") {turnserver_port = value; } // webrtc.js
 
}

function setstatusmultiple(value) {
	var statusarray = new Array();
	statusarray = value.split(" ");
	for (var i = 0; i<statusarray.length; i+=2) {
		setstatus(statusarray[i], statusarray[i+1]);
	}
}

function setstatusunknown() {
	var statuses = "stream speed battery cameratilt motion lag keyboard user dock selfstream";
	str = statuses.split(" ");
	var a;
	var i;
	for (i in str) {
		a=document.getElementById(str[i]+"_status");
		a.style.color = "#666666";
	}
}

function hiddenmessageboxShow() {
	document.getElementById("overlaydefault").style.display = "none";
	document.getElementById("login").style.display = "none";
	document.getElementById("someonealreadydrivingbox").style.display = "none";
//	document.getElementById("advancedmenubox").style.display = "none";
	document.getElementById("extrastuffcontainer").style.display = "none";
	document.getElementById("hiddenmessageboxcontainer").style.display = "";
	overlay("on");
}

function extrastuffboxShow() {
	document.getElementById("overlaydefault").style.display = "none";
	document.getElementById("login").style.display = "none";
	document.getElementById("someonealreadydrivingbox").style.display = "none";
//	document.getElementById("advancedmenubox").style.display = "none";
	document.getElementById("hiddenmessageboxcontainer").style.display = "none";
	document.getElementById("extrastuffcontainer").style.display = "";
	overlay("on");
}

function keyBoardPressed(event) {
	if (enablekeyboard) {
		var keycode = event.keyCode;
		if (keycode == 32 || keycode == 83) { // space bar or S
			move("stop");
		}
		else if (keycode == 38 || keycode == 87) { // up arrow or W
			event.preventDefault(); // supress scrolling
			move("forward");
		}
		else if (keycode == 40 || keycode == 88) { // down arrow or X
			event.preventDefault(); // supress scrolling
			move("backward");
		}
		else if (keycode == 37 || keycode == 81) { // left arrow or Q
			event.preventDefault(); // supress scrolling
			move("left");
		}
		else if (keycode == 39 || keycode == 69) { // right arrow or E
			event.preventDefault(); // supress scrolling
			move("right");
		}
		else if (keycode == 65) { // A
			nudge("left");
		}
		else if (keycode == 68) { // D
			nudge("right");
		}
		else if (keycode == 84) { nudge("forward"); } // T
		else if (keycode == 66) { nudge("backward") } // B
		else if (keycode == 49) { speedset('slow'); } // 1
		else if (keycode == 50) { speedset('med'); } // 2
		else if (keycode == 51) { speedset('fast'); } // 3
		else if (keycode == 82) { camera('upabit'); } // R
		else if (keycode == 70) { camera('horiz'); } // F
		else if (keycode == 86) { camera('downabit'); } // V
		else if (keycode == 77) { // M
			if (document.getElementById("menu_menu_over").style.display == "none") {
				mainmenu('mainmenulink'); 
			} else { javascript: popupmenu('menu', 'close'); }
		}
		else if (keycode == 73) { // I
			if (streammode == "stop") {
				publish("camera");
			} else {
				publish("stop");
			}
		}
		else if (keycode == 67) { // C - command window
			mainmenu('mainmenulink'); 
			popupmenu('menu', 'show', null, null, document.getElementById("advanced_menu").innerHTML);
			oculuscommanddivShow();
		}
		// if (steeringmode == "forward") { document.getElementById("forward").style.backgroundImage = "none"; }
		
		else if (keycode == 89 && broadcastmicon==false && pushtotalk==true && (broadcasting=="mic" || broadcasting=="camandmic")) { // Y
			oculusPrimeplayerSWF.unmutePlayerMic();
			broadcastmicon = true;
			setstatus("selfstream","mic ON");
			//message("unmute player mic", "orange");
		}
		else if (keycode == 80 && streammode != "stop") { // P
			autodock('start');
			autodock('go');
		}
		else if (keycode == 76) { 
			if (spotlightlevel == 0) {
				callServer("spotlight", "50"); 
				spolightlevel = 50;
			}
			else {
				callServer("spotlight", "0");
				spolightlevel = 0;
			}
		}
	}
}

function keyBoardReleased(event) {
	if (enablekeyboard) {
		var keycode = event.keyCode;
		if (keycode == 84 && pushtotalk==true && (broadcasting=="mic" || broadcasting=="camandmic")) {
			oculusPrimeplayerSWF.mutePlayerMic();
			setstatus("selfstream",broadcasting);
			broadcastmicon = false;
			//message("mute player mic", "orange");
		}
	}
}

function pushtotalktoggle() {
	var str;
	if (pushtotalk) {
		pushtotalk = false;
		oculusPrimeplayerSWF.unmutePlayerMic();
		str = "false";
	}
	else {
		pushtotalk = true;
		oculusPrimeplayerSWF.mutePlayerMic();
		str = "true";			
	}
	if (broadcasting=="mic" || broadcasting=="camandmic") {
		setstatus("selfstream",broadcasting);
		if (str=="true") { broadcastmicon  = false; }
		else { broadcastmicon = true; }
	}
	message("sending: sending push T to talk "+str, sentcmdcolor);
	callServer("pushtotalktoggle", str);
	lagtimer = new Date().getTime(); // has to be *after* message()
}

function motionenabletoggle() {
	message("sending: motion enable/disable", sentcmdcolor);
	callServer("motionenabletoggle", "");
	lagtimer = new Date().getTime(); // has to be *after* message()
	// overlay("off");
}

function muteROVonmovetoggle() {
	message("sending: mute ROV mic onmove toggle", sentcmdcolor);
	callServer("muterovmiconmovetoggle", "");
	lagtimer = new Date().getTime(); // has to be *after* message()
	// overlay("off");
}
function move(str) {
	message("sending command: "+str, sentcmdcolor);
	callServer("move", str);
	lagtimer = new Date().getTime();
}

function nudge(direction) {
	message("sending command: nudge " + direction, sentcmdcolor);
	callServer("nudge", direction);
	lagtimer = new Date().getTime();
}

function docklinetoggle(str) {
	var a = document.getElementById("dockline");
	var b = document.getElementById("docklineleft");
	var c = document.getElementById("docklineright");
	if (str=="") {
		if (a.style.display == "") { str="off"; }
		else { str="on"; }
	}
	if (str=="off") { 
		a.style.display="none"; 
		b.style.display="none";
		c.style.display="none";
		popupmenu("context", "close");
	}
	if (str=="on" && streammode != "stop") {
		clicksteer("on");
		a.style.display="";
		b.style.display="";
		c.style.display="";
		docklineposition();
		
		var str = "<table><tr><td style='height: 5px'></td></tr></table>";
				str += "<img src='images/dockline.png'"; 
		str += " border='0' height='22' style='vertical-align: middle'> ";
		str += "Manual dock <table><tr><td style='height: 20px'></td></tr></table>";
		str += "&nbsp; <a href='javascript: dock();'><span class='cancelbox'>&#x2714;</span> GO</a>";
		str += "&nbsp; <a href='javascript: docklinetoggle(&quot;off&quot;); move(&quot;stop&quot;);'><span "; 
		str += "class='cancelbox'><b>X</b></span> CANCEL</a>";
		str += "<table><tr><td style='height: 20px'></td></tr></table>";
		str += "<a href='javascript: camera(&quot;reverse&quot;)'>reverse camera</a>";
		
	    var link = document.getElementById("docklink");
	    var xy = findpos(link);
	    popupmenu("context", "show", xy[0] + link.offsetWidth + 60, xy[1] + link.offsetHeight, str, null, 1,1);

	}
}

function docklineposition(n) {
	var i = ctroffset * videoscale/100;
	if (n) { i = n; }
	var a = document.getElementById("dockline");
	var b = document.getElementById("video");
	var xy = findpos(b);
	var c = document.getElementById("docklineleft");
	var d = document.getElementById("docklineright");
	var top = xy[1];
	var height = b.offsetHeight;
	var ctr = xy[0] + b.offsetWidth/2;
	a.style.left = (ctr+i) +"px";
	c.style.left = (ctr+i-(docklinewidth/2))+"px";
	d.style.left = (ctr+i+(docklinewidth/2))+"px";
	a.style.top = top + "px";
	a.style.height = height + "px";
	c.style.top = height/2 - 60 + top + "px";
	c.style.height = "120px";
	d.style.top = height/2 - 60 + top + "px";
	d.style.height = "120px";
}

// function speech() {
	// var a = document.getElementById('speechbox');
	// var str = a.value;
	// a.value = "";
	// callServer("speech", str);
	// message("sending command: say '" + str + "'", sentcmdcolor, 0);
	// lagtimer = new Date().getTime();
// }

function mainmenu(id) {
	streamdetailspopulate();
	rovvolumepopulate();
	lightpopulate();
	str = document.getElementById("main_menu").innerHTML;
	if (admin) { str += document.getElementById("main_menu_admin").innerHTML; }
	else { str += document.getElementById("main_menu_nonadmin").innerHTML; }
	x = null;
	y = null;
	if (id) {
		var link = document.getElementById(id);
		var xy = findpos(link);
		
//		var w = mainmenuwidth;
//		if (w == null) { w=285; }
		w = 0;
		
		x = xy[0]+ w; // =150;
		xy = findpos(document.getElementById("video"));
		y = xy[1]+4; // +30
	}
	popupmenu("menu", "show", x, y, str, null, 0, 0);
	if (mainmenuwidth == null) {
		var m = document.getElementById("menu_menu_contents");
		mainmenuwidth = m.offsetWidth;
		
	}
//	resized();
}

function rovvolumepopulate() {
	var str = "<table><tr>";
	for (var i=0; i<=10; i++) {
		if (i==0 || i==10) { wpx = 14; fsz=19; } else { wpx = 8; fsz=15; }
		str+="<td  id='rvoltd"+i+"' class='slider' style='width: "+wpx+"px;'" +
		" onmouseover='rovvolumeover(&quot;"+i+"&quot;)'" +
		" onmouseout='rovvolumeout(&quot;"+i+"&quot;)' onclick='rovvolumeclick(&quot;"+i+"&quot;)'>" +
				"<span style='font-size: "+fsz+"px'" +
				" >|</span></td>";
	}
	str += "</tr></table>";
	document.getElementById("rovvolumecontrol").innerHTML = str;
	var b=document.getElementById("rvoltd"+parseInt(rovvolume/10));
	b.style.backgroundColor = "#aaaaaa";
	b.style.color = "#111111";
}

function rovvolumeover(i) {
	if (i*10 == rovvolume) return;
	document.getElementById("rvoltd"+i).style.backgroundColor = "#555555";
}

function rovvolumeout(i) {
	if (i*10 == rovvolume) return;
	document.getElementById("rvoltd"+i).style.backgroundColor = "#111111"; 
}

function rovvolumeclick(vol) {
	// unset old
	var b = document.getElementById("rvoltd"+parseInt(rovvolume/10));
	b.style.color = "#cccccc";
	b.style.backgroundColor = "#111111";
	
	// set new
	var a = document.getElementById("rvoltd"+vol);
	a.style.color = "#111111";
	a.style.backgroundColor = "#aaaaaa";

	message("sending system volume: "+ parseInt(vol*10)+"%", sentcmdcolor);
	callServer("setsystemvolume", parseInt(vol*10));
	lagtimer = new Date().getTime();
	rovvolume = vol*10;
}

function lightpopulate() {
	var a = document.getElementById("spotlightcontrol");
	var str = "<table><tr>";
	for (var i=0; i<=10; i++) {
		if (i==0 || i==10) { wpx = 14; fsz=19; } else { wpx = 8; fsz=15; }
		str+="<td  id='lighttd"+i+"' class='slider' style='width: "+wpx+"px;'" +
		" onmouseover='lightover(&quot;"+i+"&quot;)'" +
		" onmouseout='lightout(&quot;"+i+"&quot;)' onclick='lightclick(&quot;"+i+"&quot;)'>" +
				"<span style='font-size: "+fsz+"px'" +
				" >|</span></td>";
	}
	str += "</tr></table>";
	a.style.display = "";
	a.innerHTML = str;
	var b=document.getElementById("lighttd"+(spotlightlevel/10).toString());
	b.style.backgroundColor = "#aaaaaa";
	b.style.color = "#111111";
	
	// floodlight:
	var c = document.getElementById("floodlightcontrol");
	var str = "<table><tr>";
	for (var i=0; i<=10; i++) {
		if (i==0 || i==10) { wpx = 14; fsz=19; } else { wpx = 8; fsz=15; }
		str+="<td  id='floodlighttd"+i+"' class='slider' style='width: "+wpx+"px;'" +
		" onmouseover='floodlightover(&quot;"+i+"&quot;)'" +
		" onmouseout='floodlightout(&quot;"+i+"&quot;)' onclick='floodlightclick(&quot;"+i+"&quot;)'>" +
				"<span style='font-size: "+fsz+"px'" +
				" >|</span></td>";
	}
	str += "</tr></table>";
	c.style.display = "";
	c.innerHTML = str;
	var d=document.getElementById("floodlighttd"+(floodlightlevel/10).toString());
	d.style.backgroundColor = "#aaaaaa";
	d.style.color = "#111111";
}

function lightover(i) {
	if (i*10 == spotlightlevel) return;
	document.getElementById("lighttd"+i).style.backgroundColor = "#555555";
}

function lightout(i) {
	if (i*10 == spotlightlevel) return;
	document.getElementById("lighttd"+i).style.backgroundColor = "#111111";
}

function lightclick(level) {
	// unset old
	var b = document.getElementById("lighttd"+(spotlightlevel/10).toString());
	b.style.color = "#cccccc";
	b.style.backgroundColor = "#111111";
	
	// set new
	var a = document.getElementById("lighttd"+level);
	a.style.color = "#111111";
	a.style.backgroundColor = "#aaaaaa";
	
	message("sending spotlight brightness: "+ (level*10).toString()+"%", sentcmdcolor);
	callServer("spotlight", (level*10).toString());
	lagtimer = new Date().getTime();
}

function floodlightover(i) {
	if (i*10 == floodlightlevel) return;
	document.getElementById("floodlighttd"+i).style.backgroundColor = "#555555";
}

function floodlightout(i) {
	if (i*10 == floodlightlevel) return;
	document.getElementById("floodlighttd"+i).style.backgroundColor = "#111111";
}

function floodlightclick(level) {
	// unset old
	var b = document.getElementById("floodlighttd"+(floodlightlevel/10).toString());
	b.style.color = "#cccccc";
	b.style.backgroundColor = "#111111";
	
	// set new
	var a = document.getElementById("floodlighttd"+level);
	a.style.color = "#111111";
	a.style.backgroundColor = "#aaaaaa";
	
	message("sending floodlight: "+ (level*10).toString()+"%", sentcmdcolor);
	callServer("floodlight", (level*10).toString());
	lagtimer = new Date().getTime();
}

function streamdetailspopulate() {
	if (streamdetails.length > 0) {
		var s = [];
		s = streamdetails;
		var i = 1;
		var a= document.getElementById("low_specs");
		a.innerHTML = "size:"+s[i]+"x"+s[i+1]+" fps:"+s[i+2]+" quality:"+s[i+3];
		i = 5;
		a= document.getElementById("med_specs");
		a.innerHTML = "size:"+s[i]+"x"+s[i+1]+" fps:"+s[i+2]+" quality:"+s[i+3];
		i = 9;
		a= document.getElementById("high_specs");
		a.innerHTML = "size:"+s[i]+"x"+s[i+1]+" fps:"+s[i+2]+" quality:"+s[i+3];
		i = 13;
		a= document.getElementById("custom_specs");
		a.innerHTML = "size:"+s[i]+"x"+s[i+1]+" fps:"+s[i+2]+" quality:"+s[i+3];
//		a = document.getElementById(streamdetails[0].slice(1)+"_bull");
//		a.style.visibility="visible";
		streamSettingsBullSet(streamdetails[0].slice(1));
		resized();
	}

	var b = document.getElementById("recordlink");
	if (recordmode != "stop") {
		b.innerHTML = "stop recording";
		b.href="javascript: callServer('record','false');";
	}
	else  {
		b.innerHTML = "record";
		b.href="javascript: callServer('record','true');";
	}

}

function streamSettingsBullSet(str) {
	var settings = ["low", "med", "high", "custom"];
	for (var i = 0; i < settings.length; i++) {
//		debug(setting);
		document.getElementById(settings[i]+"_bull").style.visibility = "hidden";
	}
	document.getElementById(str+"_bull").style.visibility = "visible";
}

function keypress(e) {
	var keynum;
	if (window.event) {
		keynum = e.keyCode;
	}// IE
	else if (e.which) {
		keynum = e.which;
	} // Netscape/Firefox/Opera
	return keynum;
}

function battStats() {
	message("sending command: battstats", sentcmdcolor);
	callServer("battstats", "");
	lagtimer = new Date().getTime();
}

function emailsettings() {
	var str = document.getElementById("emailsettings").innerHTML;
	popupmenu("menu","show",null,null,str);
	callServer("getemailsettings","");
	message("request email settings values", sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
}

function emailsettingsdisplay(str) { // called by server via flashplayer
	message("email settings values received", "green");
	splitstr = str.split(" ");
	document.getElementById('email_smtp_server').value = splitstr[0];
	document.getElementById('email_smtp_port').value = splitstr[1];
	document.getElementById('email_username').value = splitstr[2];
	document.getElementById('email_password').value = splitstr[3];
	document.getElementById('email_from_address').value = splitstr[4];
	document.getElementById('email_to_address').value = splitstr[5];

}

function emailsettingssend() {
	var str = "";
	var s =  document.getElementById('email_smtp_server').value;
	if (!/\S/.test(s)) s="disabled";
	str += s.trim() + " ";

	s =  document.getElementById('email_smtp_port').value;
	if (!/\S/.test(s)) s="25";
	str += s.trim() + " ";
	
	s =  document.getElementById('email_username').value;
	if (!/\S/.test(s)) s="disabled";
	str += s.trim() + " ";

	s =  document.getElementById('email_password').value;
	if (!/\S/.test(s)) s="disabled";
	str += s.trim() + " ";

	s =  document.getElementById('email_from_address').value;
	if (!/\S/.test(s)) s="disabled";
	str += s.trim() + " ";

	s =  document.getElementById('email_to_address').value;
	if (!/\S/.test(s)) s="disabled";
	str += s.trim() + " ";

	callServer("emailsettingsupdate", str);
	message("sending email settings", sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
}

function drivingsettings() {
	var str = document.getElementById("drivingsettings").innerHTML;
	popupmenu("menu","show",null,null,str);
	callServer("getdrivingsettings","");
	message("request driving settings values", sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
//	resized();
}

function drivingsettingsdisplay(str) { // called by server via flashplayer
	message("driving settings values received", "green");
	splitstr = str.split(" ");
	document.getElementById('slowoffset').value = splitstr[0];
	document.getElementById('medoffset').value = splitstr[1];
	document.getElementById('nudgedelay').value = splitstr[2];
	document.getElementById('maxclicknudgedelay').value = splitstr[3];
	document.getElementById('maxclickcam').value = splitstr[4];
	document.getElementById('fullrotationdelay').value = splitstr[5];
	document.getElementById('onemeterdelay').value = splitstr[6];
	document.getElementById('steeringcomp').value = splitstr[7];
	document.getElementById('camhorizpos').value = splitstr[8];
	document.getElementById('camreversepos').value = splitstr[9];
}

function drivingsettingssend() {
	str =  document.getElementById('slowoffset').value + " "
			+ document.getElementById('medoffset').value + " "
			+ document.getElementById('nudgedelay').value + " "
			+ document.getElementById('maxclicknudgedelay').value + " "
			+ document.getElementById('maxclickcam').value + " "
			+ document.getElementById('fullrotationdelay').value + " "
			+ document.getElementById('onemeterdelay').value + " "
			+ document.getElementById('steeringcomp').value + " "	
			+ document.getElementById('camhorizpos').value+ " "
			+ document.getElementById('camreversepos').value;
	callServer("drivingsettingsupdate", str);
	message("sending driving settings values: " + str, sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
}

function camera(fn) {
	if (!(fn=="stop" && !cameramoving)) {
		callServer("cameracommand", fn);
		message("sending tilt command: " + fn, sentcmdcolor);
		lagtimer = new Date().getTime(); // has to be *after* message()
		if (fn == "up" || fn=="down") { 
			cameramoving = true; 
		}
		else { cameramoving = false; }
	}
}

function tilttest() {
	var str = document.getElementById('tilttestposition').value;
	callServer("camtilt", str);
	message("sending tilt position: " + str, sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
}

function speedset(str) {
	callServer("speed", str);
	message("sending speedset: " + str, sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
}

function dock() {
	callServer("dock", "");
	message("sending: dock", sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
	if (steeringmode == "forward") { document.getElementById("forward").style.backgroundImage = "none"; }
}

function autodock(str) {
	if (str=="start" &! autodocking && streammode != "stop") {
 		overlay("off");
		clicksteeron = false;
//		document.getElementById("video").style.zIndex = "0";
		clicksteer("off");
		
	    var str = "<img src='images/charge.png'"; 
		str += " border='0' height='26' style='vertical-align: middle'> " +
	    		"Dock with charger <table><tr><td style='height: 7px'></td></tr></table>";
	    str+="Get the dock in view, within 3 meters"
    	str+="<table><tr><td style='height: 20px'></td></tr></table>";
	    
	    str+="<a href='javascript: autodock(&quot;go&quot;);'>";
	    str+= "<span class='cancelbox'>&#x2714;</span> GO</a> &nbsp; &nbsp;";
	    str+="<a href='javascript: autodock(&quot;cancel&quot;);'>";
	    str+= "<span class='cancelbox'><b>X</b></span> CANCEL</a>";
	    str+="<table><tr><td style='height: 20px'></td></tr></table>";
		str += "<a href='javascript: docklinetoggle(&quot;on&quot;);'><img src='images/dockline.png'"; 
		str += " border='0' height='22' style='vertical-align: middle'> manual dock</a>";

	    var link = document.getElementById("docklink");
	    var xy = findpos(link);
	    popupmenu("context", "show", xy[0] + link.offsetWidth + 60, xy[1] + link.offsetHeight, str, 160, 1, 1);
	}
	if (str=="cancel") {
		popupmenu("context", "close");
		docklinetoggle("off");
		clicksteer("on");
		if (autodocking) {
			callServer("autodock", "cancel");
			message("sending autodock cancel", sentcmdcolor);
			lagtimer = new Date().getTime(); // has to be *after* message()
		}
	}
	if (str=="calibrate" && streammode != "stop") {
//		overlay("off");
		popupmenu("menu","close");
		clicksteeron = false;
		document.getElementById("video").style.zIndex = "0";
		videooverlayposition();
		var a =document.getElementById("videooverlay");
	    a.onclick = autodockcalibrate;
	    var str = "Auto-Dock Calibration: <table><tr><td style='height: 7px'></td></tr></table>";
	    str+="Align robot with charging dock, reverse camera, then click within white area of target"
    	str+="<table><tr><td style='height: 11px'></td></tr></table>";
		str += "<a href='javascript: camera(&quot;reverse&quot;)'>reverse camera</a>";
    	str+="<table><tr><td style='height: 11px'></td></tr></table>";
    	str+="<a href='javascript: autodock(&quot;cancel&quot;);'>"
	    str+= "<span class='cancelbox'><b>X</b></span> CANCEL</a><br>"
	    var video = document.getElementById("video");
	    var xy = findpos(video);
	    popupmenu("context", "show", xy[0] + video.offsetWidth - 10, xy[1] + 10, str, 160, 1, 0);
	}
	if (str=="go") {
		callServer("autodock", "go");
		message("sending autodock-go", sentcmdcolor);
		lagtimer = new Date().getTime(); // has to be *after* message()
		autodocking = true;
	    var str = "Auto Dock: <table><tr><td style='height: 7px'></td></tr></table>";
	    str+="in progress... stand by"
		str+="<table><tr><td style='height: 11px'></td></tr></table>";
	    str+="<a href='javascript: autodock(&quot;cancel&quot;);'>"
	    str+= "<span class='cancelbox'><b>X</b></span> CANCEL</a><br>"
    	popupmenu("context","show",null,null,str);
	}
}

function autodockclick(ev) { // TODO: unused if autodock("go") used above, instead
	ev = ev || window.event;
	if (ev.pageX) {
		var x = ev.pageX;
		var y = ev.pageY;
	}
	else {
		var x = ev.clientX + document.body.scrollLeft - document.body.clientLeft;
		var y = ev.clientY + document.body.scrollTop - document.body.clientTop;
	}
	var v = document.getElementById("video");
	var xy = findpos(v);
	x -= xy[0];
	y -= xy[1];
	//alert(v.offsetLeft);
	
	var b = document.getElementById("docklinecalibratebox")
    var str = "Auto Dock: <table><tr><td style='height: 7px'></td></tr></table>";
    str+="in progress... stand by"
	str+="<table><tr><td style='height: 11px'></td></tr></table>";
    str+="<a href='javascript: autodock(&quot;cancel&quot;);'>"
    str+= "<span class='cancelbox'><b>X</b></span> CANCEL</a><br>"
    b.innerHTML = str;
	
	callServer("autodockgo", x+" "+y);
	message("sending autodock-go: " + x+" "+y, sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
	autodocking = true;
}

function autodocklock(str) {
	 clicksteer("on");
	splitstr = str.split(" ");
	//x,y,width,height
	videooverlayposition();
	var scale =2;
	var video = document.getElementById("video");
	var xy = findpos(video);
	var box = document.getElementById("facebox");
	splitstr = str.split(" "); // left top width height
	box.style.width = (splitstr[2]*scale*videoscale/100)+"px";
	box.style.height = (splitstr[3]*scale*videoscale/100)+"px";
	//box.style.left = (video.offsetLeft + (splitstr[0]*scale)) + "px";
	box.style.left = xy[0] + (video.offsetWidth/2) + 
	   (((splitstr[0]*scale) - (video.offsetWidth/2)) * (videoscale/100)) + "px";
	box.style.top = xy[1] + (video.offsetHeight/2) + 
	   (((splitstr[1]*scale) - (video.offsetHeight/2)) * (videoscale/100)) + "px";
	box.style.display = "";
	setTimeout("document.getElementById('facebox').style.display='none';",500);
}

function autodockcalibrate(ev) {
	ev = ev || window.event;
	if (ev.pageX) {
		var x = ev.pageX;
		var y = ev.pageY;
	}
	else {
		var x = ev.clientX + document.body.scrollLeft - document.body.clientLeft;
		var y = ev.clientY + document.body.scrollTop - document.body.clientTop;
	}
	var video = document.getElementById("video");
	var xy = findpos(video);
	scale = 2; // convert to 320
	var xctroffset = x - (xy[0] + (video.offsetWidth/scale)); 
	var yctroffset = y - (xy[1] + (video.offsetHeight/scale)); 
	if (!(xctroffset ==0 && yctroffset==0)) {
		xctroffset /= videoscale/100;
		yctroffset /= videoscale/100;
	}
	x = Math.round(xctroffset)+320;
	y = Math.round(yctroffset)+240;
	callServer("autodockcalibrate", x+" "+y);
	message("sending autodock-calibrate: " + x+" "+y, sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
	autodock("cancel");
}

function popupmenu(pre_id, command, x, y, str, sizewidth, x_offsetmult, y_offsetmult) {
//	var link = document.getElementById(docklink);
	var under = document.getElementById(pre_id+"_menu_under");
	var over = document.getElementById(pre_id +"_menu_over");
	var contents = document.getElementById(pre_id+"_menu_contents");
//	var maintable = document.getElementById("maintable");
	if (command=='show') {
//		var xy = findpos(link);
//		var maintablexy = findpos(maintable);

		contents.innerHTML = str;
		over.style.display = "";
		over.style.width = null; //ie fix
		over.style.height = null; //ie fix
		under.style.width = null; //ie fix
		under.style.height = null; //ie fix
		if (sizewidth != null) { contents.style.width = sizewidth + "px"; }
		
		if (x != null && y != null) {
			if (!x_offsetmult) { x_offsetmult = 0; }
			if (!y_offsetmult) { y_offsetmult = 0; }
			x = x - (x_offsetmult * over.offsetWidth);
			y = y - (y_offsetmult * over.offsetHeight); 
			
			var i = subwindows.indexOf(pre_id);
			if (windowpos[i] != null) {
				x = windowpos[i][0];
				y = windowpos[i][1];
			}
			
			over.style.left = x + "px";
			over.style.top = y + "px";		
			under.style.display = "";
		}
		var margin = 4;
		under.style.left = (over.offsetLeft - margin) + "px";
		under.style.top = (over.offsetTop -margin) + "px";
		under.style.width = (over.offsetWidth + margin*2) + "px";
		under.style.height = (over.offsetHeight + margin*2) + "px";
		//document.body.onclick = function() { popupmenu("close"); }
		over.style.width = over.offsetWidth;
		over.style.height = over.offsetHeight;
	}
	else if (command=='move') {
		document.onmousemove = function(event) { popupmenumove(event, pre_id); }
		popupmenu_xoffset = null;
		popupmenu_yoffset = null;
		document.body.onclick = null;
		if (clicksteeron && document.getElementById("videologo").style.display == "none") {  
			clicksteer("off");
			clicksteeron = true;
		}
		document.onmouseup = function () { 
			document.onmousemove = null;
			document.onmouseup = null;
			if (clicksteeron && document.getElementById("videologo").style.display == "none") {
				clicksteer("on");
			}
		}
	}
	else if (command=="close") {
		over.style.display = "none";
		under.style.display = "none";
		contents.innerHTML = "";
		//document.body.onclick = null;
	}
	else if (command=="resize") {
		over.style.width = null; //ie fix
		over.style.height = null; //ie fix
		var margin = 4;
		under.style.left = (over.offsetLeft - margin) + "px";
		under.style.top = (over.offsetTop -margin) + "px";
		under.style.width = (over.offsetWidth + margin*2) + "px";
		under.style.height = (over.offsetHeight + margin*2) + "px";
		over.style.width = over.offsetWidth;
		over.style.height = over.offsetHeight;
	}
	
	resized();
}

function popupmenumove(ev, pre_id) {
	var xy = getmousepos(ev);
	var x= xy[0];
	var y= xy[1];
	var under = document.getElementById(pre_id+"_menu_under");
	var over = document.getElementById(pre_id+"_menu_over");
	if (!popupmenu_xoffset) { popupmenu_xoffset = over.offsetLeft - x; }
	if (!popupmenu_yoffset) { popupmenu_yoffset = over.offsetTop - y; }

	over.style.left = (x + popupmenu_xoffset) + "px";
	over.style.top = (y + popupmenu_yoffset) + "px";
	var margin = 4;
	under.style.left = (over.offsetLeft - margin) + "px";
	under.style.top = (over.offsetTop -margin) + "px";
	resized();
}

function popupmenusize(pre_id, ev) {
	var xy = getmousepos(ev);
	popupmenu_xsizestart = xy[0];
	popupmenu_ysizestart = xy[1];
	
	var contents = document.getElementById(pre_id+"_menu_contents");
	popupmenu_widthstart = contents.offsetWidth - 20; 
	popupmenu_heightstart = contents.offsetHeight-18; 
	document.onmousemove = function(event) { popupmenusizedrag(event, pre_id); }
	
	document.body.onclick = null;
	if (clicksteeron && document.getElementById("videologo").style.display == "none") {  
		clicksteer("off");
		clicksteeron = true;
	}
	
	document.onmouseup = function (pre_id) { 
		document.onmousemove = null;
		document.onmouseup = null;
		if (clicksteeron && document.getElementById("videologo").style.display == "none") {
			clicksteer("on");
		}
	}
}

function popupmenusizedrag(ev, pre_id) {
	var xy = getmousepos(ev);
	var xdelta = xy[0] - popupmenu_xsizestart;
	var ydelta = xy[1] - popupmenu_ysizestart;

	var contents = document.getElementById(pre_id +"_menu_contents");
	contents.style.width = null; //ie fix
	contents.style.height = null; //ie fix
	contents.style.width = (popupmenu_widthstart + xdelta) + "px";
	contents.style.height = (popupmenu_heightstart + ydelta) + "px";

	var over = document.getElementById(pre_id +"_menu_over");	
	over.style.width = null; //ie fix
	over.style.height = null; //ie fix
	over.style.width = over.offsetWidth;
	over.style.height = over.offsetHeight;
	
	var under = document.getElementById(pre_id+"_menu_under");
	var margin = 4;
	under.style.left = (over.offsetLeft - margin) + "px";
	under.style.top = (over.offsetTop -margin) + "px";
	under.style.width = (over.offsetWidth + margin*2) + "px";
	under.style.height = (over.offsetHeight + margin*2) + "px";
	
	if (pre_id == "rosmap") {
		rmid = document.getElementById('rosmapimgdiv');
		mapimgdivwidth = contents.offsetWidth - 20 ;
		rmid.style.width = mapimgdivwidth + "px";
		mapimgdivheight = contents.offsetHeight - 17 - 
			document.getElementById("rosmapheader").offsetHeight;
		rmid.style.height = mapimgdivheight +"px";
//		debug(rmid.style.height);
	}
	
	resized();
}

function getmousepos(ev) {
	ev = ev || window.event;
	if (ev.pageX || ev.pageY) {
		var x = ev.pageX;
		var y = ev.pageY;
	}
	else {
		var x = ev.clientX + document.body.scrollLeft - document.body.clientLeft;
		var y = ev.clientY + document.body.scrollTop - document.body.clientTop;
	}
	return [x,y];
}

function debug(str) {
	document.getElementById('debugbox').style.display = "";
	document.getElementById('debugbox').innerHTML = str;
}

function keyboard(str) {
	if (str=="enable") {
		setstatus("keyboard","enabled");
		enablekeyboard = true;
		// changecss(".keycmds", "color", "#777777");
	}
	else { 
		setstatus("keyboard","disabled");
		enablekeyboard = false; 
		// changecss(".keycmds", "color", "#222222");
	}
}

/*
function changecss(theClass, element, value) {
	// Last Updated on June 23, 2009
	// documentation for this script at
	// http://www.shawnolson.net/a/503/altering-css-class-attributes-with-javascript.html
	var cssRules;
	var added = false;
	for ( var S = 0; S < document.styleSheets.length; S++) {

		if (document.styleSheets[S]['rules']) {
			cssRules = 'rules';
		} else if (document.styleSheets[S]['cssRules']) {
			cssRules = 'cssRules';
		} else {
			// no rules found... browser unknown 
		}

		for ( var R = 0; R < document.styleSheets[S][cssRules].length; R++) {
			if (document.styleSheets[S][cssRules][R].selectorText == theClass) {
				if (document.styleSheets[S][cssRules][R].style[element]) {
					document.styleSheets[S][cssRules][R].style[element] = value;
					added = true;
					break;
				}
			}
		}
		if (!added) {
			if (document.styleSheets[S].insertRule) {
				document.styleSheets[S].insertRule(theClass + ' { ' + element
						+ ': ' + value + '; }',
						document.styleSheets[S][cssRules].length);
			} else if (document.styleSheets[S].addRule) {
				document.styleSheets[S].addRule(theClass, element + ': '
						+ value + ';');
			}
		}
	}
}
*/

function crosshairs(state) {
    crosshairsposition(); 
    if (state == "on") { //turn on
    	document.getElementById("crosshair_top").style.display = "";
        document.getElementById("crosshair_right").style.display = "";
        document.getElementById("crosshair_bottom").style.display = "";
        document.getElementById("crosshair_left").style.display = "";
        cspulsate = true;
        crosshairspulsate();
    }
    else { //turn off
    	document.getElementById("crosshair_top").style.display = "none";
        document.getElementById("crosshair_right").style.display = "none";
        document.getElementById("crosshair_bottom").style.display = "none";
        document.getElementById("crosshair_left").style.display = "none";
        cspulsate = false;
        clearTimeout(cspulsatetimer);
    }
}

function crosshairsposition() {
    var hfromctr=cspulsatenum;
    var vfromctr=cspulsatenum;
    var video = document.getElementById("video");
    var xy = findpos(video);
    var videow = video.offsetWidth; // + ctroffset*2;
    var videoh = video.offsetHeight;

    var a=document.getElementById("crosshair_top");
    a.style.left = (videow/2 + xy[0]) + "px";
    a.style.top = (xy[1] + videoh/2 - vfromctr - parseInt(a.style.height)) + "px";

    var b=document.getElementById("crosshair_right");
    b.style.left = (videow/2 + hfromctr + xy[0]) + "px";
    b.style.top = (videoh/2 + xy[1]) + "px";

    var c=document.getElementById("crosshair_bottom");
    c.style.left = (videow/2 + xy[0]) + "px";
    c.style.top = (videoh/2 + vfromctr + xy[1]) + "px";

    var d=document.getElementById("crosshair_left");
	d.style.left = (videow/2 - hfromctr - parseInt(d.style.width) + xy[0]) + "px";
	d.style.top = (videoh/2 + xy[1]) + "px";
}

function crosshairspulsate() {
	if (cspulsate == true) {
		cspulsatenum += cspulsatenumdir	;
		if (cspulsatenum >25 ) { cspulsatenum=25; cspulsatenumdir = -1; }
		if (cspulsatenum <20 ) { cspulsatenum=20; cspulsatenumdir = 1; }
		crosshairsposition();
		cspulsatetimer = setTimeout("crosshairspulsate();",100);
	}
}

function systemcall(str,conf) {
	overlay("off");
	if (str=="") {
		var a = document.getElementById('usersyscommand');
		str=a.value;
		a.value = "";
//		overlay('off');
		message("sending system command: "+str,sentcmdcolor);
	}
	if (conf=="y") {
		if (confirm("execute:\n'"+str+"'\nare you sure?")) { 
			message("sending system command: "+str,sentcmdcolor);
			callServer("systemcall",str); 
		}
		else { message("sytem command aborted", sentcmdcolor); }
	}
	else { callServer("systemcall",str); }
}

function usersyscommanddivHide() {
	document.getElementById('usersyscommanddiv').style.display='none';
	popupmenu('menu','resize');
}

function usersyscommanddivShow() {
	document.getElementById('usersyscommanddiv').style.display='';
	document.getElementById('usersyscommand').value='';
	document.getElementById('usersyscommand').focus();
	popupmenu('menu','resize');
}

function oculuscommanddivHide() {
	document.getElementById('oculuscommanddiv').style.display='none';
	popupmenu('menu','resize');
}

function oculuscommanddivShow() {
	document.getElementById('oculuscommanddiv').style.display='';
	if (!lastcommand) lastcommand = null;
	document.getElementById('oculuscommand').focus();
	popupmenu('menu','resize');
	setTimeout("document.getElementById('oculuscommand').value=lastcommand;", 50);
}

function oculuscommandgo() {
	var str = document.getElementById('oculuscommand').value;
	lastcommand = str;
	str = str.replace(/^\s+|\s+$/g, ''); // strip
	var cmd = str.split(" ",1);
	var val = str.substring(cmd[0].length+1);
	//debug("str = *"+str+"*<br>cmd = *"+cmd[0]+"*<br>val = *"+val+"*<br>cmd length = "+cmd[0].length);
	callServer(cmd[0], val);
	message("sending: "+cmd[0]+" "+val,"orange");
	lagtimer = new Date().getTime(); // has to be *after* message()
}


function chatdivHide() {
	document.getElementById('chatdiv').style.display='none';
	popupmenu('menu','resize');
}

function chatdivShow() {
	document.getElementById('chatdiv').style.display='';
	document.getElementById('chatbox_input').value='';
	document.getElementById('chatbox_input').focus();
	popupmenu('menu','resize');
}

function writesetting(value){
	callServer('writesetting', value);
	message("sending setting: "+value,sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
}

function holdservo(str) {
	callServer("holdservo",str);
	message("sending holdservo "+str,sentcmdcolor);
	lagtimer = new Date().getTime(); // has to be *after* message()
}

function restart() {
	if (confirm("restart server application\nare you sure?")) { 
	  message("sending restart: "+str,sentcmdcolor);
	  callServer('restart','');
	}
}

function softwareupdate(command,value) {
	if (command=="check") { 
		callServer("softwareupdate","check");
		message("sending software update request",sentcmdcolor);
		lagtimer = new Date().getTime(); // has to be *after* message()
//		overlay('off');
	}
	if (command=="available") {
		if (confirm(value)) {
			message("sending install software download request",sentcmdcolor);
			lagtimer = new Date().getTime(); // has to be *after* message()
			callServer("softwareupdate","download");
		}
		else { message("software update declined",sentcmdcolor); }
	}
	if (command=="downloadcomplete") {
		var str = "Software update download complete.\n";
		str += "Update will take effect on next server restart.\n\n";
		str += "Do you want to restart server now?";
		if (confirm(str)) { callServer('restart',''); }
	}
	if (command =="version") {
		message("sending software version request",sentcmdcolor);
		lagtimer = new Date().getTime(); // has to be *after* message()
		callServer("softwareupdate","versiononly");
//		overlay("off");
	}
}

function clicksteer(str) {
	var sendmsg = false;
	if (str=="") { sendmsg = true; }
	if (str=="" && !clicksteeron) { str = "on"; }
	if (str == "on") { 
		document.getElementById("video").style.zIndex = "0";
		clicksteeron = true;
		videooverlayposition();
		var a =document.getElementById("videooverlay");

		a.onmouseover = videoOverlayMouseOver;
	    a.onmouseout = videoOverlayMouseOut;
	    a.onclick = videoOverlayClick;
	    // above event function assign WORKs in ie9. Must be something else, like 0 size window?
	    
	    a.style.cursor="crosshair";
	}
	else {
		str="off";
		document.getElementById("video").style.zIndex = "5";
		clicksteeron = false;
		//str = 'off'
	}
//	overlay('off');
	if (sendmsg) message("clicksteer "+str,"green");
}

function videooverlayposition() {
	var a = document.getElementById("videooverlay");
    var video = document.getElementById("video");
    var xy = findpos(video);
    a.style.width = video.offsetWidth + "px";
    a.style.height = video.offsetHeight + "px";
    a.style.left = xy[0] + "px";
    a.style.top = xy[1] + "px";
}

function videoOverlayMouseOver() {
	if (videomouseaction && clicksteeron) {
		videooverlayposition();
		videoOverlaySetMousePosGrabber();
		document.getElementById("videocursor_top").style.display="";
		document.getElementById("videocursor_bottom").style.display="";
		document.getElementById("videocursor_left").style.display="";
		document.getElementById("videocursor_right").style.display="";
		crosshairs("on");
//		var a=document.getElementById("videocursor_ctr"); // <canvas>
//		a.style.display = "";
//		try {
//
//			var ctx = a.getContext("2d");
//			ctx.strokeStyle = "#34ae2b"; // "#45F239";
//			ctx.beginPath();
//			ctx.arc(25,25,24,0,Math.PI*2,true);
//			ctx.stroke();
//		}
//		catch(err) { 
//			html5=false;
//		} // some non html5 browsers
//		html5=false;
//		if (html5==false) {
//			a.style.display = "none";
		var a=document.getElementById("videocursor_ctr_html4");
		a.style.display = "";
//
//		}
		videoOverlayGetMousePos(); // ie fix
	}
}

function videoOverlaySetMousePosGrabber() {
	document.onmousemove = videoOverlayGetMousePos;
}
	
function videoOverlayGetMousePos(ev) {
	ev = ev || window.event;
	if (ev.pageX || ev.pageY) {
		var x = ev.pageX;
		var y = ev.pageY;
	}
	else {
		var x = ev.clientX + document.body.scrollLeft - document.body.clientLeft;
		var y = ev.clientY + document.body.scrollTop - document.body.clientTop;
	}
    //debug(x+" "+y);
	var b = document.getElementById("videocursor_top");
	b.style.left = x + "px";
    var video = document.getElementById("video");
    var xy = findpos(video);
    b.style.top = xy[1] + "px";
    var th = y - xy[1] -15; if (th<0) { th=0; } 
    b.style.height = th + "px";
	var c = document.getElementById("videocursor_bottom");
	c.style.left = x + "px";
    c.style.top = (y + 16) + "px";
    var bh = xy[1] + video.offsetHeight - y -15; if (bh<0) { bh=0; }
    c.style.height = bh + "px";
    var d = document.getElementById("videocursor_left");
    d.style.left = xy[0] + "px";
    d.style.top = y + "px";
    var lw = x - xy[0] - 15; if (lw<0) { lw=0; }
    d.style.width = lw + "px";
    var e = document.getElementById("videocursor_right");
    e.style.top = y + "px";
    e.style.left = (x +16) + "px";
    var rw = xy[0] + video.offsetWidth - x -15; if (rw<0) { rw=0; }
    e.style.width = rw + "px";
//    if (html5) { var f = document.getElementById("videocursor_ctr"); }
//    else { var f = document.getElementById("videocursor_ctr_html4"); }
    var f = document.getElementById("videocursor_ctr_html4");
	f.style.display = "";
	f.style.left = (x-15) + "px";
	f.style.top = (y-15) + "px";
	document.onmousemove = null;
	videooverlaymouseposinterval = setTimeout("videoOverlaySetMousePosGrabber();", 20)
}

function videoOverlayClick(ev) {
	if (videomouseaction && clicksteeron) {
		ev = ev || window.event;
		if (ev.pageX || ev.pageY) {
				var x = ev.pageX;
				var y = ev.pageY;
		}
		else {
			var x = ev.clientX + document.body.scrollLeft - document.body.clientLeft;
			var y = ev.clientY + document.body.scrollTop - document.body.clientTop;
		}
		var video = document.getElementById("video");
		var xy = findpos(video);
		scale = 2; // convert to 320
		var xctroffset = x - (xy[0] + (video.offsetWidth/scale)); 
		var yctroffset = y - (xy[1] + (video.offsetHeight/scale)); 
		if (Math.abs(xctroffset) < 30*videoscale/100 ) { xctroffset = 0; }
		else { flashvidcursor("videocursor_top"); flashvidcursor("videocursor_bottom"); }
		if (Math.abs(yctroffset) < 15*videoscale/100) { yctroffset = 0; }
		else { flashvidcursor("videocursor_left"); flashvidcursor("videocursor_right"); }
		if (!(xctroffset ==0 && yctroffset==0)) {
			xctroffset /= videoscale/100;
			yctroffset /= videoscale/100;
			var str = Math.round(xctroffset)+" "+Math.round(yctroffset);
			callServer("clicksteer",str);
			message("sending: clicksteer "+str, sentcmdcolor);
			lagtimer = new Date().getTime(); // has to be *after* message()
		}
	}
}

//		ctroffset = Math.round(ctroffsettemp/(videoscale/100));


function videoOverlayMouseOut() {
	if (videomouseaction && clicksteeron) {
		document.onmousemove = null;
		clearTimeout(videooverlaymouseposinterval);
		document.getElementById("videocursor_top").style.display="none";
		document.getElementById("videocursor_bottom").style.display="none";
		document.getElementById("videocursor_left").style.display="none";
		document.getElementById("videocursor_right").style.display="none";
//		document.getElementById("videocursor_ctr").style.display="none";
		document.getElementById("videocursor_ctr_html4").style.display="none";
		crosshairs("off");
	}
}

function flashvidcursor(id) {
	var a =document.getElementById(id);
	var clr1 = "#ffffff";
	var clr2 = "#45F239";
	var str1 = "document.getElementById('"+id+"').style.borderColor='"+clr1+"';";
	str1 += "document.getElementById('"+id+"').style.borderWidth='2px';";
	var str2 = "document.getElementById('"+id+"').style.borderColor='"+clr2+"';";
	str2 += "document.getElementById('"+id+"').style.borderWidth='1px';";
	eval(str1);
	setTimeout(str2, 100);
	setTimeout(str1, 200);
	setTimeout(str2, 300);
}

function overlay(str) {
	var a=document.getElementById("overlay");
	var c=document.getElementById("overlaycontents");
	if (str=="on") { a.style.display = ""; c.style.display = ""; resized(); }
	if (str=="off") { a.style.display = "none"; c.style.display = "none"; resized(); }

}

function createCookie(name,value,days) {
	if (days) {
		var date = new Date();
		date.setTime(date.getTime()+(days*24*60*60*1000));
		var expires = "; expires="+date.toGMTString();
	}
	else var expires = "";
	document.cookie = name+"="+value+expires+"; path=/";
}

function readCookie(name) {
	var nameEQ = name + "=";
	var ca = document.cookie.split(';');
	for(var i=0;i < ca.length;i++) {
		var c = ca[i];
		while (c.charAt(0)==' ') c = c.substring(1,c.length);
		if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);
	}
	return null;
}

function eraseCookie(name) {
	createCookie(name,"",-1);
}

function loginfromcookie() {
	var str = ""; 
	str = readCookie("auth");
	oculusPrimeplayerSWF.connect(str);
	logintimer = setTimeout("eraseCookie('auth'); window.location.reload()", logintimeout);
}

function login() {
	document.getElementById("overlaydefault").style.display = "none";
	document.getElementById("login").style.display = "";
	document.getElementById("user").focus();	
}

function loginsend() {
	document.getElementById("overlaydefault").style.display = "";
	document.getElementById("login").style.display = "none";
	var str1= document.getElementById("user").value;
	var str2= document.getElementById("pass").value;
	var str3= document.getElementById("user_remember").checked;
	if (str3 == true) { str3="remember"; }
	else { eraseCookie("auth"); }
	
	commLogin(str1, str2, str3);
	logintimer = setTimeout("window.location.reload()", logintimeout);
}

function logout() {
	eraseCookie("auth");
	callServer("logout","");
	setTimeout("window.location.reload()", 250);
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
	callServer("driverexit","");
}

function steeringmouseover(id, str) {
	if (!(id == "forward" && steeringmode == "forward")) {
		//document.getElementById(id).style.backgroundImage = "url(images/steering_icon_highlight.gif)";
		var xy = findpos(document.getElementById(id));
		var highlitebox = document.getElementById("mousecontrolshighlitebox");
		highlitebox.style.left = xy[0]+"px";
		highlitebox.style.top = xy[1]+"px";
		highlitebox.style.backgroundColor = "#333333";
		highlitebox.style.display = "";
	}
	document.getElementById("steering_textbox").innerHTML = id.toUpperCase();
	if (str) {
		document.getElementById("steeringkeytextbox").innerHTML = str; 
	}
}

function steeringmouseout(id) {
	if (!(id == "forward" && steeringmode == "forward")) {
		//document.getElementById(id).style.backgroundImage = "none";
		document.getElementById("mousecontrolshighlitebox").style.display = "none";
		if (
			(id == "backward" && steeringmode == "backward") || 
			(id == "rotate right" && steeringmode == "rotate right") ||
			(id == "rotate left" && steeringmode == "rotate left") ||
			(id == "bear right" && steeringmode == "bear right") ||
			(id == "bear left" && steeringmode == "bear left") ||
			(id == "bear right backward" && steeringmode == "bear right backward") ||
			(id == "bear left backward" && steeringmode == "bear leftbackward") 
			) {
			if (!autodocking) {
				move("stop");
				steeringmode="stop";
			}
		}
	}
	if ( id == "camera up" || id=="camera down") { camera('stop'); }
	document.getElementById("steering_textbox").innerHTML = "";
	document.getElementById("steeringkeytextbox").innerHTML = "";
}

//forwardhighlitebox
function steeringmousedown(id) {
	if (id != "forward" && id != "nudge left" && id != "nudge right" 
		&& id != "camera up" && id != "camera down" && id != "camera horizontal"
		&& id != "speed slow" && id != "speed medium" && id != "speed fast"
		&& steeringmode == "forward") {
		//document.getElementById("forward").style.backgroundImage = "none";
		document.getElementById("forwardhighlitebox").style.display = "none";
	}

	//document.getElementById(id).style.backgroundImage = "url(images/steering_icon_selected.gif)";
	var highlitebox = document.getElementById("mousecontrolshighlitebox");
	highlitebox.style.backgroundColor = "#cccccc";
	
	if (id == "forward") { 
		move("forward");
		var xy = findpos(document.getElementById("forward"));
		var fwhighlitebox = document.getElementById("forwardhighlitebox");
		fwhighlitebox.style.left = xy[0]+"px";
		fwhighlitebox.style.top = xy[1]+"px";
		fwhighlitebox.style.backgroundColor = "#999999";
		fwhighlitebox.style.display = "";
		} 
	if (id == "backward") { move("backward"); }
	if (id == "rotate right") { move("right"); }
	if (id == "rotate left") { move("left"); }
	if (id == "nudge right") { nudge("right"); id = null; }
	if (id == "nudge left") { nudge("left"); id = null; }
	if (id == "nudge forward") { nudge("forward"); }
	if (id == "nudge backward") { nudge("backward"); }
	if (id == "stop") { move("stop"); }
	if (id == "bear left") { move("bear_left"); }
	if (id == "bear right") { move("bear_right"); }
	if (id == "bear left backward") { move("bear_left_bwd"); }
	if (id == "bear right backward") { move("bear_right_bwd"); }
	if (id == "camera up") { camera("up"); id=null; }
	if (id == "camera down") { camera("down"); id=null; }
	if (id == "camera horizontal") { camera("horiz"); id=null; }
	if (id == "speed slow") { speedset("slow"); id=null; }
	if (id == "speed medium") { speedset("med"); id=null; }
	if (id == "speed fast") { speedset("fast"); id=null; }
	if (id) {
		steeringmode = id;
	}
}

function steeringmouseup(id) {
	if (steeringmode != "forward" || (id == "nudge left" || id == "nudge right"
		|| id=="camera up" || id=="camera down" || id=="camera horizontal"
		|| id=="speed slow" || id=="speed medium" || id=="speed fast" )) {
		//document.getElementById(id).style.backgroundImage = "url(images/steering_icon_highlight.gif)";
		var highlitebox = document.getElementById("mousecontrolshighlitebox");
		highlitebox.style.backgroundColor = "#444444";

		if (
			(id == "backward" && steeringmode == "backward") || 
			(id == "rotate right" && steeringmode == "rotate right") ||
			(id == "rotate left" && steeringmode == "rotate left") ||
			(id == "bear right" && steeringmode == "bear right") ||
			(id == "bear left" && steeringmode == "bear left") ||
			(id == "bear right backward" && steeringmode == "bear right backward") ||
			(id == "bear left backward" && steeringmode == "bear left backward") 
			) {
			if (!autodocking) {
				move("stop");
				steeringmode="stop";
			}
		}
		if ( id == "camera up" || id=="camera down") { camera('stop'); }
	}
}

function videologo(state) {
	    
	var i = document.getElementById("videologo");

	if (state != "on" && state != "off") {
		state = (i.style.display == "none") ? "off" : "on";
	}
	
    var video = document.getElementById("video");
    var xy = findpos(video);
    var s = document.getElementById("stream");
	if (state=="on") { 
		i.style.display = ""; 
		s.style.display="none"; 
	}
	if (state=="off") { 
		i.style.display = "none"; 
		s.style.display="";
	}
	
	i.width = video.offsetWidth;
	i.height = video.offsetHeight;
	
    var x = xy[0] + (video.offsetWidth/2) - (i.width/2);
    var y = xy[1] + (video.offsetHeight/2) - (i.height/2);
    i.style.left = x + "px";
    i.style.top = y + "px";
}

function docklinecalibrate(str) {
	if (str == "start" && streammode != "stop") {
		
//		overlay("off");
		popupmenu("menu","close");
		clicksteeron = false;
		document.getElementById("video").style.zIndex = "0";
		videooverlayposition();
		var a =document.getElementById("videooverlay");
	    a.onclick = docklineclick;
	    ctroffsettemp = ctroffset;
	    document.getElementById("dockline").style.display = "";
	    document.getElementById("docklineleft").style.display = "";
	    document.getElementById("docklineright").style.display = "";
	    docklineposition();

	    var str = "Manual Dock Guide Calibration:"
	    str += "<table><tr><td style='height: 7px'></td></tr></table>";
	    str += "Align robot with charging dock, then click screen to align guide line";
	    str += "<table><tr><td style='height: 7px'></td></tr></table>";
	    str += "<a href='javascript: docklinecalibrate(&quot;save&quot;);'>";
	    str += "<span class='cancelbox'>&#x2714;</span> SAVE</a> &nbsp; &nbsp; ";
	    str += "<a href='javascript: docklinecalibrate(&quot;cancel&quot;);'>";
	    str += "<span class='cancelbox'><b>X</b></span> CANCEL</a><br>";
	    
	    var video = document.getElementById("video");
	    var xy = findpos(video);
	    popupmenu("context", "show", xy[0] + video.offsetWidth - 10, xy[1] + 10, str, 180, 1, 0);
	}

	if (str == "save") {
		docklinetoggle("off");
		ctroffset = Math.round(ctroffsettemp/(videoscale/100));
		popupmenu("close");
		clicksteer("on");
		message("sending dockline position: " + ctroffset, sentcmdcolor);
		callServer("docklineposupdate", ctroffset);
		lagtimer = new Date().getTime();
	}
	if (str == "cancel") {
		docklinetoggle("off");
		clicksteer("on");
	}
}

function docklineclick(ev) {
	ev = ev || window.event;
	if (ev.pageX) {
		var x = ev.pageX;
	}
	else {
		var x = ev.clientX + document.body.scrollLeft - document.body.clientLeft;
	} 
	// var x = ev.clientX + document.body.scrollLeft - document.body.clientLeft;
	var video = document.getElementById("video");
	var xy = findpos(video);
	ctroffsettemp  = x - (xy[0] + (video.offsetWidth/2));
	docklineposition(ctroffsettemp);
}

function account(str) { // change_password, password_update  DONE
	// // change_other_pass, add_user, delete_user, newuser_add, change_username
	if (str == "change_password" && admin) {
		var a= document.getElementById("extendedsettingsbox");
		document.getElementById('user_name').innerHTML = username;
		var str = document.getElementById("changepassword_admin").innerHTML;
		popupmenu("menu","show",null,null,str);
	}
	if (str == "change_password" &! admin) {
		var a= document.getElementById("extendedsettingsbox");
		document.getElementById('user_name_nonadmin').innerHTML = username;
		var str = document.getElementById("changepassword_nonadmin").innerHTML;
		popupmenu("menu","show",null,null,str);
	}
	if (str == "password_update") {
		var pass = document.getElementById('userpass').value;
		var passagain = document.getElementById('userpass_again').value;
		if (pass != passagain || pass=="") { message("ERROR: passwords didn't match, try again", "green"); }
		else {
			message("sending new password", sentcmdcolor);
			callServer("password_update", pass);
			lagtimer = new Date().getTime();
//			if (admin) {
//				popupmenu('menu', 'show', null, null, document.getElementById('account_settings').innerHTML);
//			}
//			else { mainmenu(); }
		}
	}
	if (str=="add_user") {
		var str = document.getElementById("adduser").innerHTML;
		popupmenu("menu","show",null,null,str);
	}
	if (str=="newuser_add") {
//		overlay("off");
		var user = document.getElementById('newusername').value;
		var pass = document.getElementById('newuserpass').value;
		var passagain = document.getElementById('newuserpass_again').value;
		var oktosend = true;
		var msg = "";
		if (pass != passagain || pass=="") {
			oktosend = false;
			msg += "*error: passwords didn't match, try again "; 
		}
		if (/\s+/.test(user)) { 
			oktosend = false;
			msg += "*error: no spaces allowed in user name "; 
		} 
		if (/\s+/.test(pass)) { 
			oktosend = false;
			msg += "*error: no spaces allowed in password "; 
		}
		if (msg != "") { message(msg, sentcmdcolor); }
		if (oktosend) {
			message("sending new user info", sentcmdcolor);
			callServer("new_user_add", user + " " + pass);
			lagtimer = new Date().getTime();
//			popupmenu('menu', 'show', null, null, document.getElementById('account_settings').innerHTML);
		}
	}	
	if (str=="delete_user") {
		//deleteuser deluserlist 
		userlistcalledby = "deluser";
		callServer("user_list","");
		message("request user list", sentcmdcolor);
		lagtimer = new Date().getTime();
	}
	if (str=="change_extra_pass") {  
		userlistcalledby = "changeextrapass";
		callServer("user_list","");
		message("request user list", sentcmdcolor);
		lagtimer = new Date().getTime();
	}
	if (str=="change_username") {
		document.getElementById('user_name2').innerHTML = username;
		var str = document.getElementById("changeusername").innerHTML;
		popupmenu("menu","show",null,null,str);
	}
	if (str=="username_update") { //
		var user = document.getElementById('usernamechanged').value;
		var pass = document.getElementById('usernamechangedpass').value;
		if (/\s+/.test(user)) { 
			message("no spaces allowed in user name", "orange"); 
			return;
		} 
		if (user!="") {
			message("sending new username", sentcmdcolor);
			callServer("username_update", user+" "+pass);
			lagtimer = new Date().getTime();
//			popupmenu('menu', 'show', null, null, document.getElementById('account_settings').innerHTML);
//			document.getElementById("extendedsettingsbox").style.display = "none";
		}
	}
}

function relayserver(str) {
	if (str==null) {
		// callServer("readsetting", "relayserver");
		var str = document.getElementById("relayserverlogin").innerHTML;
		popupmenu("menu","show",null,null,str);
	}
	else if (str=="connect") {
		callServer("relayconnect", document.getElementById('relayserverhost').value +" "+
			document.getElementById('relayserveruser').value +" "+
			document.getElementById('relayserverpassword').value);
	}
	else if (str=="disable") {
		callServer("relaydisable", "");
		relay = false;
		setstatus("connection","connected");
	}
}

function userlistpopulate(list) {
	message("user list received", "green");
	users = list.split(" ");
	if (userlistcalledby == "deluser") {
		userlistcalledby = null;
		var a= document.getElementById("deluserlist");
		if (users.length == 0) { a.innerHTML = "no extra users"; }
		else {
			var str="";
			var i;
			for (i in users) {
				if (users[i] != "") {
					str += "<a class='blackbg' href='javascript: deluserconf(&quot;"+users[i]+"&quot;);'>"
						+users[i]+"</a><br>";
				}
			}
			a.innerHTML = str;
		}
		str = document.getElementById("deleteuser").innerHTML;
		popupmenu("menu","show",null,null,str);
	}
	if (userlistcalledby=="changeextrapass") {
		userlistcalledby=null;
		var a= document.getElementById("changepassuserlist");
		if (users.length == 0) { a.innerHTML = "no extra users"; }
		else {
			var str="";
			var i;
			for (i in users) {
				if (users[i] != "") {
					str += "<a class='blackbg' href='javascript: openbox(&quot;passfield_"
						+users[i]+"&quot;);'>"+users[i]+"</a><br>";
					str += "<div id='passfield_"+users[i]+"' style='padding-left: 5px; display: none; padding-bottom: 7px;'>";
					str += "new password: ";
					str += "<input id='extrauserpass_"+users[i]+"' class='inputbox' type='password' name='password' size='10'"; 
					str += "onfocus='keyboard(&quot;disable&quot;); this.style.backgroundColor=&quot;#000000&quot;'"; 
					str += "onblur='keyboard(&quot;enable&quot;); this.style.backgroundColor=&quot;#151515&quot;'><br/>";
					str += "re-enter new password: ";
					str += "<input id='extrauserpassagain_"+users[i]+"' class='inputbox' type='password' name='password' size='10'"; 
					str += "onfocus='keyboard(&quot;disable&quot;); this.style.backgroundColor=&quot;#000000&quot;'"; 
					str += "onblur='keyboard(&quot;enable&quot;); this.style.backgroundColor=&quot;#151515&quot;'><br/>";
					str += "<a class='blackbg' href='javascript: updateextrapass(&quot;"+users[i]+"&quot;);'>";
					str += "<span class='cancelbox'>&#x2714;</span> update</a> &nbsp;";
					str += "<a class='blackbg' href='javascript: closebox(&quot;passfield_"+users[i]+"&quot;);'>";
					str += "<span class='cancelbox'><b>X</b></span> <span style='font-size: 11px'>CANCEL</span></a>";
					str += "</div>";
				}
			}
			a.innerHTML = str;
		}
		str = document.getElementById("changeextrapassword").innerHTML;
		popupmenu("menu","show",null,null,str);
	}
} 

function deluserconf(str) {
	if (confirm("delete: "+str+"\nare you sure?")) {
		popupmenu('menu', 'show', null, null, document.getElementById('account_settings').innerHTML);
		message("request delete user: "+str, sentcmdcolor);
		callServer("delete_user", str);
		lagtimer = new Date().getTime();
	}
}

function updateextrapass(str) {
	overlay("off");
	var pass = document.getElementById('extrauserpass_'+str).value;
	var passagain = document.getElementById('extrauserpassagain_'+str).value;
	var oktosend = true;
	var msg = "";
	if (pass != passagain || pass=="") {
		oktosend = false;
		msg += "*error: passwords didn't match, try again "; 
	}
	if (/\s+/.test(pass)) { 
		oktosend = false;
		msg += "*error: no spaces allowed in password "; 
	}
	if (msg != "") { message(msg, sentcmdcolor); }
	if (oktosend) {
		message("sending new password", sentcmdcolor);
		callServer("extrauser_password_update", str+" "+pass);
		lagtimer = new Date().getTime();
		document.getElementById("extendedsettingsbox").style.display = "none";
	}
}

function closebox(str) {
	document.getElementById(str).style.display = "none";
	popupmenu("menu","resize");
}

function openbox(str) {
	document.getElementById(str).style.display = "";
	popupmenu("menu","resize");
}

// function closebox(str) {
	// document.getElementById(str).style.display = "none";
	// popupmenu("menu","resize");
// }

function disconnectOtherConnections() {
	message("request eliminate passengers: "+str, sentcmdcolor);
	callServer("disconnectotherconnections", "");
	lagtimer = new Date().getTime();
	overlay("off");
}

function speakchat(command,id) {
	var links = document.getElementById("speakchatlinks");
	var over = document.getElementById(id);
	// var under = document.getElementById("popoutbox_under");
	var linksinput = document.getElementById(id+"_input");
	if (command=='show') {
		var xy = findpos(links);
		over.style.display = "";
		over.style.left = (xy[0] + 20) + "px";
		over.style.top = (xy[1] + 22) + "px";
		// under.style.display = "";
		// under.style.left = (xy[0] - 65) + "px";
		// under.style.top = (xy[1] -5) + "px";
		// under.style.width = (over.offsetWidth + 12) + "px";
		// under.style.height = (over.offsetHeight + 10) + "px";
		linksinput.focus();
		keyboard('disable');
	}
	else {
		over.style.display = "none";
		// under.style.display = "none";
		keyboard('enable');
	}
	resized();
}

function findpos(obj) { // derived from http://bytes.com/groups/javascript/148568-css-javascript-find-absolute-position-element
	var left = 0;
	var top = 0;
	var ll = 0;
	var tt = 0;
	while(obj) {
		lb = parseInt(obj.style.borderLeftWidth);
		if (lb > 0) { ll += lb }
		tb = parseInt(obj.style.borderTopWidth);
		if (tb > 0) { tt += tb; }
		left += obj.offsetLeft;
		top += obj.offsetTop;
		obj = obj.offsetParent;
	}
	left += ll;
	top += tt;
	return [left,top];
}

function speech() {
	var a = document.getElementById('speechbox_input');
	var str = a.value;
	a.value = "";
	if (str != "") {
		callServer("speech", str);
		message("sending command: say '" + str + "'", sentcmdcolor);
		lagtimer = new Date().getTime();
	}
}

function chat() {
	var a = document.getElementById('chatbox_input');
	var str = a.value;
	a.value = "";
	if (str != "") {
		callServer("chat", "<i>"+username.toUpperCase()+"</i>: "+str);
		message("sending command: chat '" + str + "'", sentcmdcolor);
		lagtimer = new Date().getTime();
	}
}

function serverlog() {
	callServer("showlog","");
	message("request server log",sentcmdcolor);
	lagtimer = new Date().getTime();
}

function showserverlog(str) {
	var a=document.getElementById("extrastuffbox");
	a.style.width = "700px";
	extrastuffboxShow();
	a.innerHTML = str;
}

function camiconbutton(str,id) {
	var a=document.getElementById(id);
	if (str == "over") { a.style.color = "#ffffff"; a.style.backgroundColor = "#333333"; }
	else if (str == "out") { a.style.color = "#cccccc"; a.style.backgroundColor = "transparent"; }
	else if (str == "click") {
		if (id=="pubstop") { id="stop"; } // stop already in use?
		publish(id); 
	}
	else if (str== "down") { a.style.backgroundColor = "#999999"; }
	else if (str== "up")  { a.style.backgroundColor = "transparent"; }
	
	if (id=="camera" || id=="pubstop") {
		if (str=="over") {
			document.getElementById("steering_textbox").innerHTML = "CAMERA ON/OFF".toUpperCase();
			document.getElementById("steeringkeytextbox").innerHTML = "I";
		}
		else {
			document.getElementById("steering_textbox").innerHTML = "";
			document.getElementById("steeringkeytextbox").innerHTML = "";
		}
	}
	else if (id=="docklink") {
		if (str=="over") {
			document.getElementById("steering_textbox").innerHTML = "DOCK".toUpperCase();
			document.getElementById("steeringkeytextbox").innerHTML = "P";
		}
		else {
			document.getElementById("steering_textbox").innerHTML = "";
			document.getElementById("steeringkeytextbox").innerHTML = "";
		}
	}
	else if (id=="mainmenulink") {
		if (str=="over") {
			document.getElementById("steering_textbox").innerHTML = "MENU".toUpperCase();
			document.getElementById("steeringkeytextbox").innerHTML = "M";
		}
		else {
			document.getElementById("steering_textbox").innerHTML = "";
			document.getElementById("steeringkeytextbox").innerHTML = "";
		}
	}
	
}

function streamset(str) {
	if (str=="setcustom") {
		var str = document.getElementById("customstreamsettings").innerHTML;		
		popupmenu("menu", "show", null, null, str, null);
		var i=13;
		document.getElementById('vwidth').value = streamdetails[i];
		document.getElementById('vheight').value = streamdetails[i+1];
		document.getElementById('vfps').value = streamdetails[i+2];
		document.getElementById('vquality').value = streamdetails[i+3];
	}
	else if (str=="customupdate") {
		streamdetails[0] = "vcustom";
		var i=13;
		streamdetails[i] = document.getElementById('vwidth').value;
		streamdetails[i+1] = document.getElementById('vheight').value;
		streamdetails[i+2] = document.getElementById('vfps').value;
		streamdetails[i+3] = document.getElementById('vquality').value;
		if (parseInt(streamdetails[i+3]) < 0) { streamdetails[i+3] = "0"; }
		if (parseInt(streamdetails[i+2]) < 1) { streamdetails[i+2] = "1"; }
		if (parseInt(streamdetails[i+1]) < 1) { streamdetails[i+1] = "1"; }
		if (parseInt(streamdetails[i]) < 1) { streamdetails[i] = "1"; }
		var s = streamdetails[i] +"_"+ streamdetails[i+1] +"_"+ streamdetails[i+2] +"_"+ streamdetails[i+3];
		callServer("streamsettingscustom", s);
		message("sending custom stream values: " + s, sentcmdcolor);
		lagtimer = new Date().getTime(); // has to be *after* message()
		// document.getElementById("extendedsettingsbox").style.display = "none";
		overlay("off");
	}
	else {
		streamdetails[0] = "v"+str;
//		debug(streamdetails[0]);
		callServer("streamsettingsset", str);
		message("sending stream setting " + str, sentcmdcolor);
		lagtimer = new Date().getTime(); // has to be *after* message()
//		document.getElementById("extendedsettingsbox").style.display = "none";
		streamSettingsBullSet(str);
	}
}

function acknowledgeerror(str) {
	
	if (str == "true") {
		popupmenu("error", "close");
		callServer("erroracknowledged","true");
	}
	else if (str == "cancel") {
		popupmenu("error", "close");
		callServer("erroracknowledged","false");
	}
	
	else {
		
		popupmenu("menu","close");
	    
	    var video = document.getElementById("video");
	    var xy = findpos(video);
	    popupmenu("error", "show", xy[0] + video.offsetWidth - 10, xy[1] + 10, str, null, 1, 0);
	    //function popupmenu(pre_id, command, x, y, str, sizewidth, x_offsetmult, y_offsetmult) {

	}

}

function guinotify(str) {
	if (str == "ok") {
		popupmenu("aux", "close");
		callServer("state","delete guinotify");
	}
	else {
	    var video = document.getElementById("video");
	    var xy = findpos(video);
	    popupmenu("aux", "show", xy[0] + video.offsetWidth - 30, xy[1] + 30, str, null, 1, 0);				
	}
}

function maintopbar(mode) {
	if (mode=="over") {
		document.getElementById('main_menu_under').style.display='';
		document.getElementById('main_menu_contents').style.backgroundColor='#151515';
	}
	else if (mode=="out") {
		clearTimeout(maintopbarTimer);
		document.getElementById('main_menu_under').style.display='none';
		document.getElementById('main_menu_contents').style.backgroundColor='transparent';
	}
	else if (mode="overpending") {
		maintopbarTimer = setTimeout("maintopbar('over');", 250);
	}
}

/*
 * menu functions:
 * 	save open window positions
 *  	-aux, context, (error), menu, main
 * 	default window positions (takes effect next browser page reload)
 * 		-simply delete windowpositions cookie
 * 
 *  in loaded():
 *  	-check for windowpositions cookie, set values for non null global vars window_[name]_x, window_[name]_y
 *  
 *  in popupmenu(show)
 *  	-check for non null vars window_[name]_x, window_[name]_y, use instead of default 
 *  	
 * 
 */

//createCookie(name,value,days) 
//readCookie(name) 
//eraseCookie(name) 

function saveopenwindowpositions() {
	
	var value = "";
	for (var i = 0; i < subwindows.length; i++) {
		var w = document.getElementById(subwindows[i]+"_menu_over");
		if (w.style.display == "") { // if open 
			var xy = findpos(w);
			value += xy[0]+","+xy[1]+",";
			windowpos[i] = xy;
			
			if (subwindows[i]="rosmap")  saverosmapwindowpos();
			
		}
		else {
			value += "null,null,";
			windowpos[i] = null;
		}
	}
	createCookie("windowpositions",value,364);
	message("window positions saved","orange");

}

function defaultwindowpositions() {
	eraseCookie("windowpositions");
	for (var i = 0; i < subwindows.length; i++)  windowpos[i] = null;
	defaultrosmapwindowpos();
	message("default window positions, refresh page","orange");
}

function loadwindowpositions() {
	var c = readCookie("windowpositions")
	if (c == null)  return;
	
	positions=c.split(","); 
	for (var i = 0; i < subwindows.length; i++) {
		if (positions[i*2] == "null") windowpos[i] = null; //?
		else {
			windowpos[i] = [parseInt(positions[i*2]), parseInt(positions[i*2+1])];
		}
	}
	
}

function networksettings(str) {
	mainmenu(''); 

	document.getElementById("networkmenuinner").innerHTML = str;
	popupmenu("menu","show",null,null,document.getElementById("networkmenu").innerHTML);
}


// ?
// if (!Array.prototype.indexOf) {
    // Array.prototype.indexOf = function(obj, start) {
         // for (var i = (start || 0), j = this.length; i < j; i++) {
             // if (this[i] === obj) { return i; }
         // }
         // return -1;
    // }
// }

/*
 * dev functions follow
 */

var radartimer = null;
var depthviewtimer = null;


function radar(mode) {
	if (mode=="init") {		
		callServer("opennisensor", "on"); 
		message("sending opennisensor on", sentcmdcolor);
		lagtimer = new Date().getTime(); // has to be *after* message()
	}
	if (mode=="on") {	
		var v = document.getElementById("video");
		var xy = findpos(v);
		var x = xy[0]+v.offsetWidth;
		var y=xy[1];
		var str ="<a href='javascript: radar(&quot;off&quot;);'>"
		str += "<span class='cancelbox'><b>X</b></span> CLOSE</a><br>"
		str +="<div style='height: 320px; line-height: 10px;'>";
		str +="<img id='radarimg' src='frameGrabHTTP?mode=radar' alt='' onload='radarrepeat();' style='width: 240px'; height: 320px'>";
		str += "<div style='position: relative; top: -184px; left: 17px; width: 50px;'>2.0</div>";
		str += "<div style='position: relative; top: -194px; left: 200px; width: 50px;'>2.0</div>";
		str += "<div style='position: relative; top: -114px; left: 55px; width: 50px;'>1.0</div>";
		str += "<div style='position: relative; top: -124px; left: 165px; width: 50px;'>1.0</div>";
		str += "<div style='position: relative; top: -300px; left: 2px; width: 50px;'>3</div>";
		str += "<div style='position: relative; top: -310px; left: 230px; width: 50px;'>3</div>";
		str += "<div style='position: relative; top: -70px; left: 107px; width: 75px;'>";
		str +="<span style='background-color: #666666; color: #000000;'>ROV</span></div>";
		str += "</div>"
		popupmenu('radar', 'show', x, y, str, 240, 1, 0);
	}
	if (mode=="off") {
		popupmenu("radar", "close");
	}
	if (mode=="shutdown") { 
		callServer("opennisensor", "off");
		message("sending opennisensor off", sentcmdcolor);
	}
}


function radarrepeat() {
	clearTimeout(radartimer);
	radartimer = setTimeout("radarimagereload();", 50);
}

function radarimagereload() {
	radartimer = null;
	var img = document.getElementById('radarimg');
	if (img == null) return;
	img.src = "frameGrabHTTP?mode=radar&date="+new Date().getTime();
	img.onload = function() { radarrepeat(); }
}

function processedImg(mode) {
	if (mode=="load") {	
		var v = document.getElementById("video");
		var xy = findpos(v);
		var x = xy[0]+v.offsetWidth;
		var y=xy[1];
		var str ="<a href='javascript: processedImg(&quot;close&quot;);'>"
    str+= "<span class='cancelbox'><b>X</b></span> CLOSE</a><br><br>"
		str +="<div style='height: 240px; line-height: 10px;'>";
		str +="<img src='frameGrabHTTP?mode=processedImg&date=" + new Date().getTime();
		str +=	"' alt='' width='320' height='240'>";
		str += "</div>"
		popupmenu('context', 'show', x, y, str, 320, 0, 0);
	}
	if (mode=="close") {
		popupmenu("context", "close");
	}

}

function depthView(mode) {
	if (mode=="off")  { popupmenu("aux", "close"); }
	else {
		var w = 320;
		var h = 240;
		if (mode=="floorPlaneTop") {
			w = 232;
			h = 240;
		}
		else if (mode=="stereo") {
			w = 640;
			h = 339;
		}
		else if (mode=="stereoleft") {
		    w=640; h=360;
		}
		else if (mode=="stereotop") { w=435; h=320; }
		var v = document.getElementById("video");
		var xy = findpos(v);
		var x = xy[0]+v.offsetWidth;
		var y=xy[1];
		src = "frameGrabHTTP?mode="+mode;
		var str = "<a href='javascript: depthView(&quot;off&quot;);'>"
		str += "<span class='cancelbox'><b>X</b></span> CLOSE</a><br>"
		str +="<img id='depthImg' src='"+src+"' alt='' ";
		str +="onload='depthViewRepeat(&quot;"+mode+"&quot;);' "
		str += "width='"+w+"' height='"+h+"'>"
		popupmenu('aux', 'show', x, y, str, w, 1, 0);
//		radarimagereload();
	}

}

function depthViewRepeat(mode) {
	clearTimeout(depthviewtimer);
	depthviewtimer = setTimeout("depthViewImgReload('"+mode+"');", 50);
}

function depthViewImgReload(mode) {
	depthviewtimer = null;
	var img = document.getElementById('depthImg');
	if (img==null) return;
	img.src = "frameGrabHTTP?mode="+mode+"&date="+new Date().getTime();
	img.onload = function() { depthViewRepeat(mode); }
}

function imgOverVideo(mode) {
	var img = document.getElementById("videologo");
	if (mode == "on") {
		videologo("on");
		img.src = "frameGrabHTTP?mode=videoOverlayImg&date=" + new Date().getTime();
		img.onload = function() { imgOverVideoRepeat(); }
	}
	else {
		clearTimeout(radartimer);
		radartimer = null;
		img.onload = null;
		img.src = "images/eye.gif";
	}
}

function imgOverVideoRepeat() {
	clearTimeout(radartimer);
	radartimer = setTimeout("imgOverVideoReload();", 50);
}

function imgOverVideoReload() {
	radartimer = null;
	var img = document.getElementById("videologo");
	img.src = "frameGrabHTTP?mode=videoOverlayImg&date=" + new Date().getTime();
	img.onload = function() { imgOverVideoRepeat(); }
}
