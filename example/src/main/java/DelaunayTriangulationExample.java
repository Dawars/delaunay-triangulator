
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.FPSAnimator;

import io.github.jdiemke.triangulation.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Simple implementation of an incremental 2D Delaunay triangulation algorithm
 * written in Java.
 *
 * @author Johannes Diemke
 */
public class DelaunayTriangulationExample implements GLEventListener, MouseListener, MouseMotionListener {

    private static final Dimension DIMENSION = new Dimension(640, 480);

    private static final Color COLOR_TRIANGLE_FILL = new Color(26, 121, 121);
    private static final Color COLOR_SMALLEST_ANGLE = new Color(200, 121, 121);
    private static final Color COLOR_LARGEST_AREA = new Color(20, 200, 20);
    private static final Color COLOR_TRIANGLE_EDGES = new Color(5, 234, 234);
    private static final Color COLOR_HULL_EDGE = new Color(1, 120, 234);
    private static final Color COLOR_FIXED_EDGE = new Color(126, 58, 234);
    private static final Color COLOR_CIRCUM_CENTER = new Color(234, 155, 33);
    private static final Color COLOR_TRIANGLE_BORDER = new Color(241, 241, 121);
    private static final Color COLOR_BACKGROUND = new Color(47, 47, 47);

    DelaunayTriangulator delaunayTriangulator;
    List<Vector2D> pointSet = new ArrayList<>();

    Vector2D cursor = new Vector2D(0, 0);

    // display info options
    private boolean showCenters = false; // circumcircles
    private boolean showCircles = false; // circumcircles

    public static void main(String[] args) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        new DelaunayTriangulationExample();
    }

    public DelaunayTriangulationExample() {
        Frame frame = new Frame("Delaunay Triangulation Example");
        frame.setResizable(false);

        GLCapabilities caps = new GLCapabilities(GLProfile.get("GL2"));
        caps.setSampleBuffers(true);
        caps.setNumSamples(8);

        GLCanvas canvas = new GLCanvas(caps);

        canvas.addGLEventListener(this);
        canvas.setPreferredSize(DIMENSION);
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);

        frame.add(canvas);

        final FPSAnimator animator = new FPSAnimator(canvas, 25);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                new Thread(() -> {
                    animator.stop();
                    System.exit(0);
                }).start();
            }
        });

        // menu start
        initMenu(frame);
        // menu end

        frame.setVisible(true);
        frame.pack();
        animator.start();
    }

    private void initMenu(Frame frame) {
        MenuBar mb = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem newItem = new MenuItem("New...");
        MenuItem loadItem = new MenuItem("Load...");
        MenuItem saveItem = new MenuItem("Save...");
        fileMenu.add(newItem);
        fileMenu.add(loadItem);
        fileMenu.add(saveItem);
        mb.add(fileMenu);

        Menu actionMenu = new Menu("Action");
        MenuItem addVertexItem = new MenuItem("Add vertex");
        MenuItem triangulateItem = new MenuItem("Delaunay triangulation");
        MenuItem fixEdgeItem = new MenuItem("Fix edges");
        MenuItem splitEdgeItem = new MenuItem("Split edge");
        MenuItem insertCenterItem = new MenuItem("Insert vertex to circumcenter");
        MenuItem startTriangulationItem = new MenuItem("Constrained triangulation");
        MenuItem animationItem = new MenuItem("Step-by-step triangulation");
        actionMenu.add(addVertexItem);
        actionMenu.add(triangulateItem);
        actionMenu.add(splitEdgeItem);
        actionMenu.add(insertCenterItem);
        actionMenu.add(startTriangulationItem);
        actionMenu.add(animationItem);

        mb.add(actionMenu);


        Menu viewMenu = new Menu("View");
        MenuItem showCircleItem = new MenuItem("Show/hide circumcircle");
        MenuItem showCircleCenterItem = new MenuItem("Show/hide circumcircle centers");
        MenuItem showViolationItem = new MenuItem("Show/hide constraint violations");
        viewMenu.add(showCircleItem);
        viewMenu.add(showCircleCenterItem);
        viewMenu.add(showViolationItem);
        mb.add(viewMenu);

        Menu constraintMenu = new Menu("Constraints");
        MenuItem setAreaItem = new MenuItem("Set maximum area");
        MenuItem setAngleItem = new MenuItem("Set min angle"); // or radius/smallest side ration?
        constraintMenu.add(setAreaItem);
        constraintMenu.add(setAngleItem);
        mb.add(constraintMenu);

        frame.setMenuBar(mb);

        // listeners


        newItem.addActionListener(e -> {
            pointSet.clear();
            try {
                delaunayTriangulator.triangulate();
            } catch (NotEnoughPointsException e1) { }
            updateCalculations();
        });

        saveItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));

            FileNameExtensionFilter restrict = new FileNameExtensionFilter("Only .txt files", "txt");
            fileChooser.setFileFilter(restrict);

            int result = fileChooser.showSaveDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {

                String fileName = String.valueOf(fileChooser.getSelectedFile());
                if (!fileName.endsWith(".txt")) fileName += ".txt";

                try (BufferedWriter out = new BufferedWriter(new FileWriter(fileName))) {

                    out.write(String.valueOf(pointSet.size()));
                    out.newLine();
                    for (Vector2D p : pointSet) {
                        out.write(String.valueOf(p.x));
                        out.write(" ");
                        out.write(String.valueOf(p.y));
                        out.newLine();
                    }
                    // todo save triangle list
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });

        loadItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));


            FileNameExtensionFilter restrict = new FileNameExtensionFilter(".txt files", "txt");
            fileChooser.setFileFilter(restrict);

            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                System.out.println("Selected file: " + selectedFile.getAbsolutePath());

                pointSet.clear();


                // load
                try (BufferedReader in = new BufferedReader(new FileReader(selectedFile))) {
                    int count = Integer.valueOf(in.readLine());

                    for (int i = 0; i < count; i++) {
                        String line = in.readLine();
                        String[] vec = line.split(" ");
                        pointSet.add(new Vector2D(Double.parseDouble(vec[0]), Double.parseDouble(vec[1])));
                    }

                    // todo load triangles

                    System.out.println("File successfully loaded");

                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                updateCalculations();
            }
        });

        addVertexItem.addActionListener(e -> {
            MODE = EDIT_MODES.INSERT_VERTEX;
        });

        triangulateItem.addActionListener(e -> {
            try {
                delaunayTriangulator.triangulate();
            } catch (NotEnoughPointsException e1) {
                // that's fine
            }

            updateCalculations();
        });

        splitEdgeItem.addActionListener(e -> {
            MODE = EDIT_MODES.SPLIT_EDGE;
        });
        fixEdgeItem.addActionListener(e -> {
            MODE = EDIT_MODES.SET_EDGE_CONSTRAINT;
        });
        insertCenterItem.addActionListener(e -> {
            MODE = EDIT_MODES.INSERT_TO_CENTER;
        });

        showCircleItem.addActionListener(e -> showCircles = !showCircles);
        showCircleCenterItem.addActionListener(e -> showCenters = !showCenters);
    }

    enum EDIT_MODES {
        INSERT_VERTEX,
        SET_EDGE_CONSTRAINT,
        SPLIT_EDGE,
        INSERT_TO_CENTER
    }

    final EDIT_MODES DEFAULT = EDIT_MODES.INSERT_VERTEX;
    EDIT_MODES MODE = EDIT_MODES.INSERT_VERTEX;

    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glDisable(GL.GL_CULL_FACE);
        gl.glShadeModel(GL2.GL_SMOOTH);
        gl.glClearColor(COLOR_BACKGROUND.getRed() / 255.0f, COLOR_BACKGROUND.getGreen() / 255.0f,
                COLOR_BACKGROUND.getBlue() / 255.0f, 1);
        gl.glClearDepth(1.0f);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LEQUAL);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL.GL_NICEST);

        gl.setSwapInterval(1);
        gl.glDisable(GL2.GL_CULL_FACE);

        delaunayTriangulator = new DelaunayTriangulator(pointSet);
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glOrtho(0, DIMENSION.getWidth(), DIMENSION.getHeight(), 0, 1.0, -1.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
    }

    private int minAngle = -1;
    private int largestArea = -1;

    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();
        gl.glTranslatef(0.0f, 0.0f, 0.0f);

        gl.glLineWidth(1.0f);

        gl.glColor3ub((byte) COLOR_TRIANGLE_FILL.getRed(), (byte) COLOR_TRIANGLE_FILL.getGreen(), (byte) COLOR_TRIANGLE_FILL.getBlue());

        gl.glBegin(GL.GL_TRIANGLES);

        for (int i = 0; i < delaunayTriangulator.getTriangles().size(); i++) {
            Triangle2D triangle = delaunayTriangulator.getTriangles().get(i);
            Vector2D a = triangle.a;
            Vector2D b = triangle.b;
            Vector2D c = triangle.c;


            gl.glVertex2d(a.x, a.y);
            gl.glVertex2d(b.x, b.y);
            gl.glVertex2d(c.x, c.y);

        }
        if (minAngle != -1) {
            fillTriangle(gl, delaunayTriangulator.getTriangles().get(minAngle), COLOR_SMALLEST_ANGLE);
        }
        if (largestArea != -1) {
            fillTriangle(gl, delaunayTriangulator.getTriangles().get(largestArea), COLOR_LARGEST_AREA);
        }

        gl.glEnd();
        gl.glColor3ub((byte) COLOR_TRIANGLE_EDGES.getRed(), (byte) COLOR_TRIANGLE_EDGES.getGreen(),
                (byte) COLOR_TRIANGLE_EDGES.getBlue());
        gl.glBegin(GL.GL_LINES);

        for (int i = 0; i < delaunayTriangulator.getTriangles().size(); i++) {
            Triangle2D triangle = delaunayTriangulator.getTriangles().get(i);
            Vector2D a = triangle.a;
            Vector2D b = triangle.b;
            Vector2D c = triangle.c;

            gl.glVertex2d(a.x, a.y);
            gl.glVertex2d(b.x, b.y);
            gl.glVertex2d(b.x, b.y);
            gl.glVertex2d(c.x, c.y);
            gl.glVertex2d(c.x, c.y);
            gl.glVertex2d(a.x, a.y);
        }
        gl.glEnd();


        // hull
        gl.glLineWidth(3.0f);
        gl.glBegin(GL.GL_LINES);


        gl.glColor3ub((byte) COLOR_HULL_EDGE.getRed(), (byte) COLOR_HULL_EDGE.getGreen(), (byte) COLOR_HULL_EDGE.getBlue());
        for (Edge2D edge2D : hull) {
            gl.glVertex2d(edge2D.a.x, edge2D.a.y);
            gl.glVertex2d(edge2D.b.x, edge2D.b.y);
        }

        // fixed
        gl.glColor3ub((byte) COLOR_FIXED_EDGE.getRed(), (byte) COLOR_FIXED_EDGE.getGreen(), (byte) COLOR_FIXED_EDGE.getBlue());
        for (Edge2D edge2D : fixedEdges) {
            gl.glVertex2d(edge2D.a.x, edge2D.a.y);
            gl.glVertex2d(edge2D.b.x, edge2D.b.y);
        }


        gl.glEnd();

        // draw all points
        gl.glPointSize(5.5f);
        gl.glColor3f(0.2f, 1.2f, 0.25f);

        gl.glColor3ub((byte) COLOR_TRIANGLE_BORDER.getRed(), (byte) COLOR_TRIANGLE_BORDER.getGreen(),
                (byte) COLOR_TRIANGLE_BORDER.getBlue());
        gl.glBegin(GL.GL_POINTS);

        for (Vector2D vector : pointSet) {
            gl.glVertex2d(vector.x, vector.y);
        }

        gl.glEnd();

        // draw circumcenters
        if (showCenters) {
            gl.glPointSize(5.5f);

            gl.glColor3ub((byte) COLOR_CIRCUM_CENTER.getRed(), (byte) COLOR_CIRCUM_CENTER.getGreen(),
                    (byte) COLOR_CIRCUM_CENTER.getBlue());
            gl.glBegin(GL.GL_POINTS);

            for (Triangle2D triangle : delaunayTriangulator.getTriangles()) {
                Vector2D center = triangle.circumcenter;
                gl.glVertex2d(center.x, center.y);

            }
            gl.glEnd();
        }
        if (showCircles) {
            gl.glLineWidth(3.0f);
            gl.glColor3ub((byte) COLOR_CIRCUM_CENTER.getRed(), (byte) COLOR_CIRCUM_CENTER.getGreen(),
                    (byte) COLOR_CIRCUM_CENTER.getBlue());
            gl.glBegin(GL.GL_LINES);

            // draw circles
            for (Triangle2D triangle : delaunayTriangulator.getTriangles()) {
                Vector2D center = triangle.circumcenter;
                double R = triangle.radius;

                int res = 5;

                for (int i = 0; i < 360; i += res) {
                    double deg = Math.toRadians(i);

                    Vector2D p = center.add(new Vector2D(R * Math.cos(deg), R * Math.sin(deg)));
                    gl.glVertex2d(p.x, p.y);

                    deg = Math.toRadians(i + res);

                    p = center.add(new Vector2D(R * Math.cos(deg), R * Math.sin(deg)));
                    gl.glVertex2d(p.x, p.y);
                }
            }

            gl.glEnd();
        }

        // angle debug
//        Triangle2D tri = delaunayTriangulator.triangleSoup.findContainingTriangle(cursor);
//        if (tri != null) System.out.println("Angle: " + Math.toDegrees(getSmallestAngle(tri)));
    }

    private void fillTriangle(GL2 gl, Triangle2D triangle, Color color) {
        gl.glColor3ub((byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue());

        Vector2D a = triangle.a;
        Vector2D b = triangle.b;
        Vector2D c = triangle.c;

        gl.glVertex2d(a.x, a.y);
        gl.glVertex2d(b.x, b.y);
        gl.glVertex2d(c.x, c.y);
    }

    private int findSmallestAngle() {
        double minAngle = Double.MAX_VALUE;
        int smallestTri = -1;

        List<Triangle2D> tris = delaunayTriangulator.getTriangles();

        for (int i = 0; i < tris.size(); i++) {
            Triangle2D tri = tris.get(i);
            double min = getSmallestAngle(tri);
            if (minAngle > min) {
                minAngle = min;
                smallestTri = i;
            }
        }
//        System.out.println(smallestTri);
        return smallestTri;
    }

    private double getSmallestAngle(Triangle2D tri) {
        Vector2D a = tri.a;
        Vector2D b = tri.b;
        Vector2D c = tri.c;

        Double[] angle = {
                getAngle(a, b, c),
                getAngle(b, c, a),
                getAngle(c, a, b),
        };

        return Collections.min(Arrays.asList(angle));
    }

    private int findLargestArea() {
        double maxArea = Double.MIN_VALUE;
        int biggestArea = -1;

        List<Triangle2D> tris = delaunayTriangulator.getTriangles();

        for (int i = 0; i < tris.size(); i++) {
            Triangle2D tri = tris.get(i);
            Vector2D a = tri.a;
            Vector2D b = tri.b;
            Vector2D c = tri.c;

            double area = Math.abs(a.sub(b).cross(c.sub(b))) / 2;
//            System.out.println("Area: " + area);
            if (maxArea < area) {
                maxArea = area;
                biggestArea = i;
            }
        }
        return biggestArea;
    }

    /**
     * Get the ABC angle
     *
     * @param a
     * @param b
     * @param c
     * @return
     */
    private double getAngle(Vector2D a, Vector2D b, Vector2D c) {
        Vector2D ab = a.sub(b);
        Vector2D cb = c.sub(b);
        double dot = ab.dot(cb) / ab.mag() / cb.mag();
        double angle = Math.acos(dot);
//        System.out.println(Math.toDegrees(angle));
        return angle;
    }


    private void updateCalculations() {
        minAngle = findSmallestAngle();
        largestArea = findLargestArea();


        // hull only changes when point added/load (or during refinement)
        calculateHull();

        // remove invalid fixed edges
        for (Edge2D fixedEdge : (ArrayList<Edge2D>) fixedEdges.clone()) {
            if (delaunayTriangulator.triangleSoup.findOneTriangleSharing(fixedEdge) == null) {
                fixedEdges.remove(fixedEdge);
            }
        }
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    /**
     * Inserts vertex in the middle of an edge, splits adjacent triangles, updates edge constraints
     *
     * @param edge being split
     */
    public void splitEdge(Edge2D edge) {
        TriangleSoup soup = delaunayTriangulator.triangleSoup;
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

        updateCalculations();
    }

    /**
     * Insert a vertex to the circumcenter of the triangle and restore triangulation
     *
     * @param triangle
     * @return
     */
    public boolean insertCircumcenter(Triangle2D triangle) {
        Vector2D center = triangle.circumcenter;


        return true;
    }


    @Override
    public void mousePressed(MouseEvent e) {
        Point p = e.getPoint();
        Vector2D point = new Vector2D(p.x, p.y);
        TriangleSoup soup = delaunayTriangulator.triangleSoup;
        switch (MODE) {
            case SET_EDGE_CONSTRAINT:
                // toggle edge
                Edge2D edge = soup.findNearestEdge(new Vector2D(p.x, p.y));
                toggleEdge(edge);
                break;
            case INSERT_VERTEX:
                addPoints(point);
                break;
            case SPLIT_EDGE:
                Edge2D edge2split = soup.findNearestEdge(new Vector2D(p.x, p.y));
                splitEdge(edge2split);
                break;
            case INSERT_TO_CENTER:
                Triangle2D triangle = soup.findContainingTriangle(point);
                insertCircumcenter(triangle);
                break;
        }
    }

    ArrayList<Edge2D> fixedEdges = new ArrayList<>(); // set check contains by reference
    ArrayList<Edge2D> hull = new ArrayList<>();

    /**
     * Toggle edge if not convex hull
     *
     * @param edge
     */
    private void toggleEdge(Edge2D edge) {
        if (fixedEdges.contains(edge)) { // deselect
            fixedEdges.remove(edge);
        } else { // select
            if (!hull.contains(edge)) {
                fixedEdges.add(edge);
            }
        }
    }

    /**
     * Adding a point
     *
     * @param p
     */
    private void addPoints(Vector2D p) {
        pointSet.add(p);

        updateCalculations();
    }

    private void calculateHull() {
        hull.clear();

        // triangles having no neighbor - good for inner borders
        List<Triangle2D> triangles = delaunayTriangulator.getTriangles();
        for (Triangle2D tri : triangles) {
            Edge2D[] edges = {
                    new Edge2D(tri.a, tri.b),
                    new Edge2D(tri.b, tri.c),
                    new Edge2D(tri.c, tri.a),
            };

            for (Edge2D edge : edges) {
                if (delaunayTriangulator.triangleSoup.findNeighbour(tri, edge) == null)
                    hull.add(edge);
            }
        }
    }


    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
        cursor.x = 0;
        cursor.y = 0;
    }

    @Override
    public void mouseDragged(MouseEvent e) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {
        cursor.x = e.getX();
        cursor.y = e.getY();
    }
}
