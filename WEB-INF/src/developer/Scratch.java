package developer;

public class Scratch {

        public static void main(String[] args) {
    		int h = 320;
    		int maxDepthFPTV = 3500;
    		int guessedDistance = 400;
    		final int scaledGuessedDistance = guessedDistance * h/maxDepthFPTV; // pixels
    		System.out.println(scaledGuessedDistance);

			
        }
}
