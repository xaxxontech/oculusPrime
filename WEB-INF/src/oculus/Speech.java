package oculus;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;
//import com.sun.speech.freetts.audio.JavaClipAudioPlayer;

public class Speech {
	String voiceName = "kevin16";
	VoiceManager voiceManager;
	Voice voice;

	Speech() {
		this.setup();
	}

	void setup() {
		voiceManager = VoiceManager.getInstance();
		voice = voiceManager.getVoice(voiceName);

		voice.setPitch((float) 1.75);
		voice.setPitchShift((float) 0.75);
		// voice.setPitchRange(10.1); //mutace
		voice.setStyle("casual"); // "business", "casual", "robotic", "breathy"

		if (voice != null) {
			voice.allocate();
		}
	}

	void mluv(String _a) {
		if (_a == null) {
			_a = "nothing";
		}
		voice.speak(_a);
	}

	void exit() {
		voice.deallocate();
	}

}
