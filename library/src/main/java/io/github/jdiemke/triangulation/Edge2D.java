package io.github.jdiemke.triangulation;

/**
 * 2D edge class implementation.
 *
 * @author Johannes Diemke
 */
public class Edge2D {

    private final double radius; // enclosing circle
    public Vector2D a;
    public Vector2D b;

    /**
     * Constructor of the 2D edge class used to create a new edge instance from
     * two 2D vectors describing the edge's vertices.
     *
     * @param a The first vertex of the edge
     * @param b The second vertex of the edge
     */
    public Edge2D(Vector2D a, Vector2D b) {
        this.a = a;
        this.b = b;

        this.radius = a.sub(b).mag() / 2;
    }

    public boolean isEncroached(Vector2D point) {
        Vector2D middle = a.add(b).mult(0.5);
        return point.sub(middle).mag() < radius;
    }

    @Override
    public boolean equals(Object obj) {
        Edge2D e = (Edge2D) obj;
        return (e.a == a && e.b == b) || (e.a == b && e.b == a);
    }

    @Override
    public String toString() {
        return "Edge(" + a + ", " + b + ")";
    }
}