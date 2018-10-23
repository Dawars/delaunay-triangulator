
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
public class DelaunayTriangulationExample implements GLEventListener, MouseListener, KeyListener {

    private static final Dimension DIMENSION = new Dimension(640, 480);

    private static final Color COLOR_TRIANGLE_FILL = new Color(26, 121, 121);
    private static final Color COLOR_SMALLEST_ANGLE = new Color(200, 121, 121);
    private static final Color COLOR_LARGEST_AREA = new Color(20, 200, 20);
    private static final Color COLOR_TRIANGLE_EDGES = new Color(5, 234, 234);
    private static final Color COLOR_HULL_EDGE = new Color(1, 120, 234);
    private static final Color COLOR_FIXED_EDGE = new Color(126, 58, 234);
    private static final Color COLOR_TRIANGLE_BORDER = new Color(241, 241, 121);
    private static final Color COLOR_BACKGROUND = new Color(47, 47, 47);

    DelaunayTriangulator delaunayTriangulator;
    List<Vector2D> pointSet = new ArrayList<>();

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
        canvas.addKeyListener(this);

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
        MenuItem splitEdgeItem = new MenuItem("Split edge");
        actionMenu.add(splitEdgeItem);

        mb.add(actionMenu);

        frame.setMenuBar(mb);

        // listeners


        newItem.addActionListener(e -> {
            pointSet.clear();
            try {
                delaunayTriangulator.triangulate();
            } catch (NotEnoughPointsException e1) {
                // not enough vertices, that's ok
            }
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

                    System.out.println("File successfully loaded");

                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }


                try {
                    delaunayTriangulator.triangulate();
                } catch (NotEnoughPointsException e1) {
                    e1.printStackTrace();
                }
                updateCalculations();
            }
        });

        splitEdgeItem.addActionListener(e -> {
            MODE = EDIT_MODES.SPLIT_EDGE;
        });
    }

    enum EDIT_MODES {
        INSERT_VERTEX,
        SET_EDGE_CONSTRAINT,
        SPLIT_EDGE
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

        for (int i = 0; i < delaunayTriangulator.getTriangles().size(); i++) {
            Triangle2D tri = delaunayTriangulator.getTriangles().get(i);
            Vector2D a = tri.a;
            Vector2D b = tri.b;
            Vector2D c = tri.c;

            Double[] angle = {
                    getAngle(a, b, c),
                    getAngle(b, c, a),
                    getAngle(c, a, b),
            };

            Double min = Collections.min(Arrays.asList(angle));
            if (minAngle > min) {
                minAngle = min;
                smallestTri = i;
            }
        }
//        System.out.println(smallestTri);
        return smallestTri;
    }

    private int findLargestArea() {
        double maxArea = Double.MIN_VALUE;
        int biggestArea = -1;

        List<Triangle2D> tris = delaunayTriangulator.getTriangles();

        for (int i = 0; i < delaunayTriangulator.getTriangles().size(); i++) {
            Triangle2D tri = delaunayTriangulator.getTriangles().get(i);
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

    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
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
        pointSet.add(middle);
        Triangle2D[] tris = {tri1, tri2};
        for (Triangle2D tri : tris) {
            if (tri == null) // if first triangle is null, edge is not in any triangle, otherwise border
                return;

            soup.remove(tri);

            Vector2D point = null; // selecting opposite vertex to edge
            if (tri.a != edge.a && tri.a != edge.b) point = tri.a;
            if (tri.b != edge.a && tri.b != edge.b) point = tri.b;
            if (tri.c != edge.a && tri.c != edge.b) point = tri.c;


            soup.add(new Triangle2D(point, edge.a, middle));
            soup.add(new Triangle2D(point, edge.b, middle));
        }

        if (fixedEdges.contains(edge)) {
            fixedEdges.remove(edge);
            fixedEdges.add(new Edge2D(edge.a, middle));
            fixedEdges.add(new Edge2D(edge.b, middle));
        }

        updateCalculations();
    }


    @Override
    public void mousePressed(MouseEvent e) {
        Point p = e.getPoint();
        TriangleSoup soup = delaunayTriangulator.triangleSoup;
        switch (MODE) {
            case SET_EDGE_CONSTRAINT:
                // toggle edge
                Edge2D edge = soup.findNearestEdge(new Vector2D(p.x, p.y));
                toggleEdge(edge);
                break;
            case INSERT_VERTEX:
                addPoints(p);
                break;
            case SPLIT_EDGE:
                Edge2D edge2split = soup.findNearestEdge(new Vector2D(p.x, p.y));
                splitEdge(edge2split);
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
    private void addPoints(Point p) {
        pointSet.add(new Vector2D(p.x, p.y));

        try {
            delaunayTriangulator.triangulate();
        } catch (NotEnoughPointsException e1) {
        }

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
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    public static boolean isMac() {
        String OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("mac");
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if ((!isMac() && e.getExtendedKeyCode() == KeyEvent.VK_CONTROL) ||
                (isMac() && e.getExtendedKeyCode() == KeyEvent.VK_META)) {
            MODE = EDIT_MODES.SET_EDGE_CONSTRAINT;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if ((!isMac() && e.getExtendedKeyCode() == KeyEvent.VK_CONTROL) ||
                (isMac() && e.getExtendedKeyCode() == KeyEvent.VK_META)) {
            MODE = EDIT_MODES.INSERT_VERTEX;
        }

    }
}
