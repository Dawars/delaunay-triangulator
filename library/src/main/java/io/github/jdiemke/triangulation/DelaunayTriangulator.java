package io.github.jdiemke.triangulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Java implementation of an incremental 2D Delaunay triangulation algorithm.
 *
 * @author Johannes Diemke
 */
public class DelaunayTriangulator {

    private List<Vector2D> pointSet;
    public TriangleSoup triangleSoup;

    public ArrayList<Edge2D> fixedEdges = new ArrayList<>();
    public ArrayList<Edge2D> hull = new ArrayList<>(); // border edges are fixed by default

    /**
     * Constructor of the SimpleDelaunayTriangulator class used to create a new
     * triangulator instance.
     *
     * @param pointSet The point set to be triangulated
     * @throws NotEnoughPointsException Thrown when the point set contains less than three points
     */
    public DelaunayTriangulator(List<Vector2D> pointSet) {
        this.pointSet = pointSet;
        this.triangleSoup = new TriangleSoup();
    }

    /**
     * This method generates a Delaunay triangulation from the specified point
     * set.
     *
     * @throws NotEnoughPointsException
     */
    public void triangulate() throws NotEnoughPointsException {
        triangleSoup = new TriangleSoup();

        if (pointSet == null || pointSet.size() < 3) {
            throw new NotEnoughPointsException("Less than three points in point set.");
        }

        /**
         * In order for the in circumcircle test to not consider the vertices of
         * the super triangle we have to start out with a big triangle
         * containing the whole point set. We have to scale the super triangle
         * to be very large. Otherwise the triangulation is not convex.
         */
        double maxOfAnyCoordinate = 0.0d;

        for (Vector2D vector : getPointSet()) {
            maxOfAnyCoordinate = Math.max(Math.max(vector.x, vector.y), maxOfAnyCoordinate);
        }

        maxOfAnyCoordinate *= 16.0d;

        Vector2D p1 = new Vector2D(0.0d, 3.0d * maxOfAnyCoordinate);
        Vector2D p2 = new Vector2D(3.0d * maxOfAnyCoordinate, 0.0d);
        Vector2D p3 = new Vector2D(-3.0d * maxOfAnyCoordinate, -3.0d * maxOfAnyCoordinate);

        Triangle2D superTriangle = new Triangle2D(p1, p2, p3);

        triangleSoup.add(superTriangle);

        for (int i = 0; i < pointSet.size(); i++) {
            Triangle2D triangle = triangleSoup.findContainingTriangle(pointSet.get(i));

            if (triangle == null) {
                /**
                 * If no containing triangle exists, then the vertex is not
                 * inside a triangle (this can also happen due to numerical
                 * errors) and lies on an edge. In order to find this edge we
                 * search all edges of the triangle soup and select the one
                 * which is nearest to the point we try to add. This edge is
                 * removed and four new edges are added.
                 */
                Edge2D edge = triangleSoup.findNearestEdge(pointSet.get(i));

                Triangle2D first = triangleSoup.findOneTriangleSharing(edge);
                Triangle2D second = triangleSoup.findNeighbour(first, edge);

                Vector2D firstNoneEdgeVertex = first.getNoneEdgeVertex(edge);
                Vector2D secondNoneEdgeVertex = second.getNoneEdgeVertex(edge);

                triangleSoup.remove(first);
                triangleSoup.remove(second);

                Triangle2D triangle1 = new Triangle2D(edge.a, firstNoneEdgeVertex, pointSet.get(i));
                Triangle2D triangle2 = new Triangle2D(edge.b, firstNoneEdgeVertex, pointSet.get(i));
                Triangle2D triangle3 = new Triangle2D(edge.a, secondNoneEdgeVertex, pointSet.get(i));
                Triangle2D triangle4 = new Triangle2D(edge.b, secondNoneEdgeVertex, pointSet.get(i));

                triangleSoup.add(triangle1);
                triangleSoup.add(triangle2);
                triangleSoup.add(triangle3);
                triangleSoup.add(triangle4);

                legalizeEdge(triangle1, new Edge2D(edge.a, firstNoneEdgeVertex), pointSet.get(i));
                legalizeEdge(triangle2, new Edge2D(edge.b, firstNoneEdgeVertex), pointSet.get(i));
                legalizeEdge(triangle3, new Edge2D(edge.a, secondNoneEdgeVertex), pointSet.get(i));
                legalizeEdge(triangle4, new Edge2D(edge.b, secondNoneEdgeVertex), pointSet.get(i));
            } else {
                /**
                 * The vertex is inside a triangle.
                 */
                Vector2D a = triangle.a;
                Vector2D b = triangle.b;
                Vector2D c = triangle.c;

                triangleSoup.remove(triangle);

                Triangle2D first = new Triangle2D(a, b, pointSet.get(i));
                Triangle2D second = new Triangle2D(b, c, pointSet.get(i));
                Triangle2D third = new Triangle2D(c, a, pointSet.get(i));

                triangleSoup.add(first);
                triangleSoup.add(second);
                triangleSoup.add(third);

                legalizeEdge(first, new Edge2D(a, b), pointSet.get(i));
                legalizeEdge(second, new Edge2D(b, c), pointSet.get(i));
                legalizeEdge(third, new Edge2D(c, a), pointSet.get(i));
            }
        }

        /**
         * Remove all triangles that contain vertices of the super triangle.
         */
        triangleSoup.removeTrianglesUsing(superTriangle.a);
        triangleSoup.removeTrianglesUsing(superTriangle.b);
        triangleSoup.removeTrianglesUsing(superTriangle.c);
    }

    /**
     * This method legalizes edges by recursively flipping all illegal edges.
     *
     * @param triangle  The triangle
     * @param edge      The edge to be legalized
     * @param newVertex The new vertex
     */
    private void legalizeEdge(Triangle2D triangle, Edge2D edge, Vector2D newVertex) {
        if (isEdgeFixed(edge)) return; // if constrained, edge splitting will take care of this first

        Triangle2D neighbourTriangle = triangleSoup.findNeighbour(triangle, edge);

        /**
         * If the triangle has a neighbor, then legalize the edge
         */
        if (neighbourTriangle != null) {
            if (neighbourTriangle.isPointInCircumcircle(newVertex)) {
                triangleSoup.remove(triangle);
                triangleSoup.remove(neighbourTriangle);

                Vector2D noneEdgeVertex = neighbourTriangle.getNoneEdgeVertex(edge);

                Triangle2D firstTriangle = new Triangle2D(noneEdgeVertex, edge.a, newVertex);
                Triangle2D secondTriangle = new Triangle2D(noneEdgeVertex, edge.b, newVertex);

                triangleSoup.add(firstTriangle);
                triangleSoup.add(secondTriangle);

                legalizeEdge(firstTriangle, new Edge2D(noneEdgeVertex, edge.a), newVertex);
                legalizeEdge(secondTriangle, new Edge2D(noneEdgeVertex, edge.b), newVertex);
            }
        }
    }

    /**
     * Creates a random permutation of the specified point set. Based on the
     * implementation of the Delaunay algorithm this can speed up the
     * computation.
     */
    public void shuffle() {
        Collections.shuffle(pointSet);
    }

    /**
     * Shuffles the point set using a custom permutation sequence.
     *
     * @param permutation The permutation used to shuffle the point set
     */
    public void shuffle(int[] permutation) {
        List<Vector2D> temp = new ArrayList<Vector2D>();
        for (int i = 0; i < permutation.length; i++) {
            temp.add(pointSet.get(permutation[i]));
        }
        pointSet = temp;
    }

    /**
     * Returns the point set in form of a vector of 2D vectors.
     *
     * @return Returns the points set.
     */
    public List<Vector2D> getPointSet() {
        return pointSet;
    }

    /**
     * Returns the trianges of the triangulation in form of a vector of 2D
     * triangles.
     *
     * @return Returns the triangles of the triangulation.
     */
    public List<Triangle2D> getTriangles() {
        return triangleSoup.getTriangles();
    }

    /**
     * Toggle edge if not convex hull
     *
     * @param edge
     */
    public void toggleEdge(Edge2D edge) {
        if (fixedEdges.contains(edge)) { // deselect
            fixedEdges.remove(edge);
        } else { // select
            if (!hull.contains(edge)) {
                fixedEdges.add(edge);
            }
        }
    }

    public void calculateHull() {
        hull.clear();

        // triangles having no neighbor - good for inner borders
        List<Triangle2D> triangles = getTriangles();
        for (Triangle2D tri : triangles) {
            Edge2D[] edges = {
                    new Edge2D(tri.a, tri.b),
                    new Edge2D(tri.b, tri.c),
                    new Edge2D(tri.c, tri.a),
            };

            for (Edge2D edge : edges) {
                if (triangleSoup.findNeighbour(tri, edge) == null)
                    hull.add(edge);
            }
        }
    }

    /**
     * Inserts vertex in the middle of an edge, splits adjacent triangles, updates edge constraints
     *
     * @param edge being split
     */
    public void splitEdge(Edge2D edge) {
        TriangleSoup soup = triangleSoup;
        Triangle2D tri1 = soup.findOneTriangleSharing(edge);
        Triangle2D tri2 = soup.findNeighbour(tri1, edge);

        Vector2D middle = edge.a.add(edge.b).mult(0.5);

        Triangle2D[] tris = {tri1, tri2};
        for (Triangle2D tri : tris) {
            if (tri == null) // if first triangle is null, edge is not in any triangle, otherwise border
                continue;

            soup.remove(tri);

            // selecting opposing vertex to edge
            Vector2D point = tri.getNoneEdgeVertex(edge);

            soup.add(new Triangle2D(point, edge.a, middle));
            soup.add(new Triangle2D(point, edge.b, middle));
        }
        pointSet.add(middle);

        if (fixedEdges.contains(edge)) {
            fixedEdges.remove(edge);
            fixedEdges.add(new Edge2D(edge.a, middle));
            fixedEdges.add(new Edge2D(edge.b, middle));
        }

        try {
            triangulate();
        } catch (NotEnoughPointsException e) {

        }
    }

    /**
     * Insert a vertex to the circumcenter of the triangle and restore triangulation
     *
     * @param triangle
     * @return null if successful, edge that the new vertex would encroach
     */
    public Edge2D insertCircumcenter(Triangle2D triangle) {
        TriangleSoup soup = triangleSoup;

        Vector2D center = triangle.circumcenter;

        // does inserted vertex encroach an edge?
        // only check the containing triangle (visibility)
        Triangle2D containingTriangle = soup.findContainingTriangle(center);
        Edge2D[] edges = containingTriangle.getEdges();

        for (Edge2D edge : edges) {
            if (edge.isEncroached(center) && isEdgeFixed(edge)) {
                return edge;
            }
        }

        getPointSet().add(center);


        // find neighbors of triangle to retriangulate, https://en.wikipedia.org/wiki/Bowyer%E2%80%93Watson_algorithm
        List<Triangle2D> badTriangles = soup.findNeighbours(containingTriangle);

        for (Edge2D e : edges) {
            Triangle2D newTri = new Triangle2D(e.a, e.b, center);
            soup.add(newTri);
            badTriangles.add(newTri);
        }

        soup.remove(containingTriangle);

        // constrained delaunay tri
        try {
            triangulate();
        } catch (NotEnoughPointsException e) {

        }
/*
        for (Triangle2D tri : badTriangles) {
            // bad tris are constructed that a-b edge is opposite to center vertex
            legalizeEdge(tri, new Edge2D(tri.a, tri.b), center); // fixme
        }*/

        return null;
    }


    /**
     * Find encroached edges from the input graph (border and fixed)
     *
     * @return null if no such edge exists
     */
    public Edge2D findEncroachedEdge() {
        for (int i = 0; i < fixedEdges.size() + hull.size(); i++) {
            Edge2D edge = i < fixedEdges.size() ? fixedEdges.get(i) : hull.get(i - fixedEdges.size());

            for (Vector2D point : pointSet) {
                if (edge.isEncroached(point)) {
                    return edge;
                }
            }
        }
        return null;
    }

    /**
     * Fixes an encroached vertex by edge splitting
     */
    public void fixEncroached(Edge2D edge) {
        splitEdge(edge);
    }

    public boolean isEdgeFixed(Edge2D edge) {
        return fixedEdges.contains(edge) || hull.contains(edge);
    }
}