package synthetic;

/** An interface with constants, default-free methods and a nested type. */
public interface Shape {

    int CORNERS_TRIANGLE = 3;
    int CORNERS_SQUARE = 4;

    int area();

    int corners();

    /** Nested class inside an interface is implicitly static and public. */
    final class Rect implements Shape {

        private final int w;
        private final int h;

        public Rect(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public int area() {
            return w * h;
        }

        @Override
        public int corners() {
            return CORNERS_SQUARE;
        }
    }
}
