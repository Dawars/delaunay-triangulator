
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;

import com.jogamp.opengl.util.awt.TextRenderer;
import io.github.jdiemke.triangulation.*;
import javafx.util.Pair;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import static java.lang.Thread.sleep;

/**
 * Simple implementation of an incremental 2D Delaunay triangulation algorithm
 * written in Java.
 *
 * @author Johannes Diemke
 */
public class DelaunayTriangulationExample implements GLEventListener, MouseListener, MouseMotionListener, KeyListener {

    private static final Dimension DIMENSION = new Dimension(1000, 600);

    private static final Color COLOR_TRIANGLE_FILL = new Color(26, 121, 121);
    private static final Color COLOR_SMALLEST_ANGLE = new Color(200, 121, 121);
    private static final Color COLOR_LARGEST_AREA = new Color(20, 200, 20);
    private static final Color COLOR_TRIANGLE_EDGES = new Color(5, 234, 234);
    private static final Color COLOR_HULL_EDGE = new Color(1, 120, 234);
    private static final Color COLOR_FIXED_EDGE = new Color(126, 58, 234);
    private static final Color COLOR_CIRCUM_CENTER = new Color(234, 155, 33);
    private static final Color COLOR_TRIANGLE_BORDER = new Color(241, 241, 121);
    private static final Color COLOR_BACKGROUND = new Color(47, 47, 47);
    private static final Color COLOR_DEBUG_AREA = new Color(200, 91, 193, 127);
    private static final Color COLOR_DEBUG_EDGE = new Color(200, 0, 14, 127);

    DelaunayTriangulator delaunayTriangulator;
    List<Vector2D> pointSet = new ArrayList<>();

    List<Triangle2D> debugTris = new ArrayList<>();
    List<Edge2D> debugLines = new ArrayList<>();
    List<Pair<Vector2D, Double>> debugCircles = new ArrayList<>();

    Vector2D cursor = new Vector2D(0, 0);

    // display info options
    private boolean showCenters = false; // circumcircles
    private boolean showCircles = false; // circumcircles
    private String statusText = "";
    private boolean isRunning = false;
    private long animSpeed = 500;

    double areaConstraint = Double.MAX_VALUE;
    double angleConstraint = Double.MIN_VALUE;

    public static void main(String[] args) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        new DelaunayTriangulationExample();
    }

    private final GLCanvas canvas;

    public DelaunayTriangulationExample() {
        Frame frame = new Frame("Constrained Delaunay Triangulation");
        frame.setResizable(false);

        GLCapabilities caps = new GLCapabilities(GLProfile.get("GL2"));
        caps.setSampleBuffers(true);
        caps.setNumSamples(4);

        canvas = new GLCanvas(caps);

        canvas.addGLEventListener(this);
        canvas.setPreferredSize(DIMENSION);
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addKeyListener(this);

        frame.add(canvas);

        // menu start
        initMenu(frame);
        // menu end

        frame.setVisible(true);
        frame.pack();
        frame.setLocationRelativeTo(null);
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
        actionMenu.add(fixEdgeItem);
        actionMenu.add(insertCenterItem);
        actionMenu.add(startTriangulationItem);
        actionMenu.add(animationItem);

        mb.add(actionMenu);


        Menu viewMenu = new Menu("View");
        MenuItem showCircleItem = new MenuItem("Show/hide circumcircle");
        MenuItem showCircleCenterItem = new MenuItem("Show/hide circumcircle centers");
        viewMenu.add(showCircleItem);
        viewMenu.add(showCircleCenterItem);
        mb.add(viewMenu);

        Menu constraintMenu = new Menu("Constraints");
        MenuItem setAreaItem = new MenuItem("Set maximum area");
        MenuItem setAngleItem = new MenuItem("Set min angle");
        constraintMenu.add(setAreaItem);
        constraintMenu.add(setAngleItem);
        mb.add(constraintMenu);

        frame.setMenuBar(mb);

        // listeners


        newItem.addActionListener(e -> {
            pointSet.clear();
            try {
                delaunayTriangulator.triangulate();
            } catch (NotEnoughPointsException e1) {
            }
            clearDebugDrawings();
            statusText = "New canvas";

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
                loadFile(selectedFile);
            }
        });

        addVertexItem.addActionListener(e -> {
            MODE = EDIT_MODES.INSERT_VERTEX;
        });

        startTriangulationItem.addActionListener(e -> ruppersAlgorithm());

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
            canvas.display();
        });
        fixEdgeItem.addActionListener(e -> {
            MODE = EDIT_MODES.SET_EDGE_CONSTRAINT;
            canvas.display();
        });
        insertCenterItem.addActionListener(e -> {
            MODE = EDIT_MODES.INSERT_TO_CENTER;
            canvas.display();
        });

        showCircleItem.addActionListener(e -> {
            showCircles = !showCircles;
            canvas.display();
        });
        showCircleCenterItem.addActionListener(e -> {
            showCenters = !showCenters;
            canvas.display();
        });


        animationItem.addActionListener(e -> {
            isRunning = true;
            new Thread(() -> {
                clearDebugDrawings();
                int i = 0;
                Edge2D edge = delaunayTriangulator.findEncroachedEdge();

                while (getArea(largestArea) > areaConstraint || edge != null) {

                    if (edge != null) { // e is encroached by a vertex
                        debugLines.add(edge);
                        Vector2D middle = edge.a.add(edge.b).mult(0.5);
                        double radius = edge.a.sub(edge.b).mag() / 2d;

                        debugCircles.add(new Pair<>(middle, radius));

                        Vector2D middle1 = edge.a.add(edge.b.sub(edge.a).mult(0.25));
                        Vector2D middle2 = edge.a.add(edge.b.sub(edge.a).mult(0.75));

                        debugCircles.add(new Pair<>(middle1, radius / 2));
                        debugCircles.add(new Pair<>(middle2, radius / 2));
                        statusText = "Splitting edge " + edge;

                    } else {
                        debugTris.add(largestArea);
                        debugCircles.add(new Pair<>(largestArea.circumcenter, 2d));
                        statusText = "Inserting circumcenter in " + largestArea;
                    }

                    canvas.display();
                    try {
                        sleep(animSpeed);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }

                    if (edge != null) {
                        delaunayTriangulator.splitEdge(edge);
                    } else {
                        Edge2D edge2split = delaunayTriangulator.insertCircumcenter(largestArea);
                        if (edge2split != null) {
                            clearDebugDrawings();
                            debugLines.add(edge2split);

                            Vector2D middle = edge2split.a.add(edge2split.b).mult(0.5);
                            double radius = edge2split.a.sub(edge2split.b).mag() / 2d;

                            debugCircles.add(new Pair<>(middle, radius));

                            Vector2D middle1 = edge2split.a.add(edge2split.b.sub(edge2split.a).mult(0.25));
                            Vector2D middle2 = edge2split.a.add(edge2split.b.sub(edge2split.a).mult(0.75));

                            debugCircles.add(new Pair<>(middle1, radius / 2));
                            debugCircles.add(new Pair<>(middle2, radius / 2));
                            canvas.display();

                            try {
                                sleep(animSpeed);
                            } catch (InterruptedException e1) {
                                e1.printStackTrace();
                            }

                            delaunayTriangulator.splitEdge(edge2split);
                        }
                    }


                    try {
                        sleep(animSpeed);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }

                    clearDebugDrawings();
                    updateCalculations();
                    edge = delaunayTriangulator.findEncroachedEdge();

                    i++;

                }

                statusText = "Finished";
            }).start();
        });


        setAreaItem.addActionListener(e -> {
            String response = JOptionPane.showInputDialog(frame,
                    "Maximum area limit: ",
                    "Set max area limit", JOptionPane.PLAIN_MESSAGE);

            try {
                areaConstraint = Double.parseDouble(response);
            } catch (NumberFormatException ex) {
                System.err.println("Not a number");
            }

            canvas.display();
        });

        setAngleItem.addActionListener(e -> {
            String response = JOptionPane.showInputDialog(frame,
                    "Minimum angle limit: ",
                    "Set min angle limit", JOptionPane.PLAIN_MESSAGE);

            try {
                angleConstraint = Double.parseDouble(response);
            } catch (NumberFormatException ex) {
                System.err.println("Not a number");
            }

            canvas.display();
        });
    }

    private void clearDebugDrawings() {
        debugLines.clear();
        debugTris.clear();
        debugCircles.clear();
        statusText = "";
    }

    private void loadFile(File filename) {
        try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
            int count = Integer.valueOf(in.readLine());

            for (int i = 0; i < count; i++) {
                String line = in.readLine();
                String[] vec = line.split(" ");
                pointSet.add(new Vector2D(Double.parseDouble(vec[0]), Double.parseDouble(vec[1])));
            }

            // todo load triangles


            clearDebugDrawings();
            updateCalculations();
            canvas.display();
            System.out.println("File successfully loaded");

        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

    }

    private void ruppersAlgorithm() {
//        while (angle || area crit not satisfied){
        Edge2D edge = delaunayTriangulator.findEncroachedEdge();
        while (getSmallestAngle(minAngle) < angleConstraint || getArea(largestArea) > areaConstraint || edge != null) {

            stepAlgorithm(false);

            updateCalculations();
            canvas.display();

            edge = delaunayTriangulator.findEncroachedEdge();

        }
    }

    /**
     * @return true if can continue (call again)
     */
    private void stepAlgorithm(boolean debug) {

        Edge2D edge = delaunayTriangulator.findEncroachedEdge();
        Triangle2D tri2Insert = null;
        if (edge == null) {
            tri2Insert = getSmallestAngle(minAngle) < angleConstraint ? minAngle : getArea(largestArea) > areaConstraint ? largestArea : null;
        }


        if (debug) {
            clearDebugDrawings();
            if (edge != null) { // e is encroached by a vertex
                debugLines.add(edge);
                Vector2D middle = edge.a.add(edge.b).mult(0.5);
                double radius = edge.a.sub(edge.b).mag() / 2d;

                debugCircles.add(new Pair<>(middle, radius));

                Vector2D middle1 = edge.a.add(edge.b.sub(edge.a).mult(0.25));
                Vector2D middle2 = edge.a.add(edge.b.sub(edge.a).mult(0.75));

                debugCircles.add(new Pair<>(middle1, radius / 2));
                debugCircles.add(new Pair<>(middle2, radius / 2));
                statusText = "Splitting edge " + edge;

            } else {
                debugTris.add(tri2Insert);
                debugCircles.add(new Pair<>(tri2Insert.circumcenter, 2d));
                statusText = "Inserting circumcenter in " + tri2Insert;
            }

            canvas.display();
            try {
                sleep(animSpeed);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }
        if (edge != null) {
            delaunayTriangulator.splitEdge(edge);
        } else {
            Edge2D edge2split = delaunayTriangulator.insertCircumcenter(tri2Insert);
            if (edge2split != null) {
                if (debug) {
                    clearDebugDrawings();
                    debugLines.add(edge2split);

                    Vector2D middle = edge2split.a.add(edge2split.b).mult(0.5);
                    double radius = edge2split.a.sub(edge2split.b).mag() / 2d;

                    debugCircles.add(new Pair<>(middle, radius));

                    Vector2D middle1 = edge2split.a.add(edge2split.b.sub(edge2split.a).mult(0.25));
                    Vector2D middle2 = edge2split.a.add(edge2split.b.sub(edge2split.a).mult(0.75));

                    debugCircles.add(new Pair<>(middle1, radius / 2));
                    debugCircles.add(new Pair<>(middle2, radius / 2));
                    canvas.display();

                    try {
                        sleep(animSpeed);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }

                delaunayTriangulator.splitEdge(edge2split);
            }
        }

        updateCalculations();
        canvas.display();
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == 's') {
            Edge2D edge = delaunayTriangulator.findEncroachedEdge();

            if (getSmallestAngle(minAngle) < angleConstraint || getArea(largestArea) > areaConstraint || edge != null) {
                stepAlgorithm(true);
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    enum EDIT_MODES {
        INSERT_VERTEX,
        SET_EDGE_CONSTRAINT,
        SPLIT_EDGE,
        INSERT_TO_CENTER
    }

    String getEditModeString(EDIT_MODES mode) {
        switch (mode) {
            case INSERT_VERTEX:
                return "Add Vertex";
            case INSERT_TO_CENTER:
                return "Insert Vertex to Circumcircle";
            case SPLIT_EDGE:
                return "Split Edge";
            case SET_EDGE_CONSTRAINT:
                return "Toggle Edge Constraint";
            default:
                return "";
        }
    }

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


        loadFile(new File("square.txt"));
        try {
            delaunayTriangulator.triangulate();
        } catch (NotEnoughPointsException e) {
        }
        updateCalculations();
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glOrtho(0, DIMENSION.getWidth(), DIMENSION.getHeight(), 0, 1.0, -1.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();

    }

    private Triangle2D minAngle;
    private Triangle2D largestArea;

    public void display(GLAutoDrawable drawable) {
        TriangleSoup soup = delaunayTriangulator.triangleSoup;

        GL2 gl = drawable.getGL().getGL2();

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();
        gl.glTranslatef(0.0f, 0.0f, 0.0f);

        gl.glLineWidth(1.5f);

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

        if (minAngle != null) {
            fillTriangle(gl, minAngle, COLOR_SMALLEST_ANGLE);
        }
        if (largestArea != null) {
            fillTriangle(gl, largestArea, COLOR_LARGEST_AREA);
        }

        gl.glEnd();

        // triangle edges
        gl.glColor3ub((byte) COLOR_TRIANGLE_EDGES.getRed(), (byte) COLOR_TRIANGLE_EDGES.getGreen(),
                (byte) COLOR_TRIANGLE_EDGES.getBlue());
        gl.glBegin(GL.GL_LINES);

        for (int i = 0; i < soup.getTriangles().size(); i++) {
            Triangle2D triangle = soup.getTriangles().get(i);
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
        gl.glLineWidth(5.0f);
        gl.glBegin(GL.GL_LINES);


        gl.glColor3ub((byte) COLOR_HULL_EDGE.getRed(), (byte) COLOR_HULL_EDGE.getGreen(), (byte) COLOR_HULL_EDGE.getBlue());
        for (Edge2D edge2D : delaunayTriangulator.hull) {
            gl.glVertex2d(edge2D.a.x, edge2D.a.y);
            gl.glVertex2d(edge2D.b.x, edge2D.b.y);
        }

        gl.glColor3ub((byte) COLOR_FIXED_EDGE.getRed(), (byte) COLOR_FIXED_EDGE.getGreen(), (byte) COLOR_FIXED_EDGE.getBlue());
        // fixed
        for (Edge2D edge2D : delaunayTriangulator.fixedEdges) {
            gl.glVertex2d(edge2D.a.x, edge2D.a.y);
            gl.glVertex2d(edge2D.b.x, edge2D.b.y);
        }


        gl.glColor3ub((byte) COLOR_DEBUG_EDGE.getRed(), (byte) COLOR_DEBUG_EDGE.getGreen(), (byte) COLOR_DEBUG_EDGE.getBlue());
        for (Edge2D edge2D : debugLines) {
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

        // debug tris

        drawTriangles(gl, debugTris, COLOR_DEBUG_AREA, COLOR_DEBUG_EDGE);


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

        // circles
        gl.glLineWidth(3.0f);
        gl.glBegin(GL.GL_LINES);

        if (showCircles) { // circumcircles
            for (Triangle2D triangle : delaunayTriangulator.getTriangles()) {
                Vector2D center = triangle.circumcenter;
                double R = triangle.radius;

                drawCircle(gl, COLOR_CIRCUM_CENTER, center, R);
            }
        }

        for (Pair<Vector2D, Double> circle : debugCircles) {

            drawCircle(gl, COLOR_DEBUG_EDGE, circle.getKey(), circle.getValue());
        }

        gl.glEnd();

        // text rendering

        int width = drawable.getSurfaceWidth();
        int height = drawable.getSurfaceHeight();
        renderer.beginRendering(width, height);
        renderer.setSmoothing(false);
        renderer.setColor(1.0f, 1f, 1f, 1f);

        renderer.draw(statusText, 20, height - 40);

        renderer.draw(getEditModeString(MODE), width - 20 - 300, height - 40);

        if (delaunayTriangulator.getTriangles() != null)
            renderer.draw("Number of triangles: " + delaunayTriangulator.getTriangles().size(), 20, 120);

        if (minAngle != null && largestArea != null) {
            double smallestAngle = getSmallestAngle(minAngle);
            double biggestArea = getArea(largestArea);
            renderer.draw("Biggest area: " + df.format(biggestArea) + " px²", 20, 80);
            renderer.draw("Smallest angle: " + df.format(smallestAngle) + "°", 20, 40);
        }

        if (minAngle != null && largestArea != null) {
            renderer.draw("Area limit: " + (areaConstraint == Double.MAX_VALUE ? "Inf" : df.format(areaConstraint) + " px²"), width - 300 - 20, 80);
            renderer.draw("Angle limit: " + df.format(angleConstraint) + "°", width - 300 - 20, 40);
        }
        renderer.endRendering();

    }

    DecimalFormat df = new DecimalFormat("#.##");

    private void drawTriangles(GL2 gl, List<Triangle2D> tris, Color colorDebugArea, Color colorDebugEdge) {
        gl.glLineWidth(1.0f);

        gl.glColor4ub((byte) colorDebugArea.getRed(), (byte) colorDebugArea.getGreen(), (byte) colorDebugArea.getBlue(),
                (byte) colorDebugArea.getAlpha());


        gl.glBegin(GL.GL_TRIANGLES);
        for (Triangle2D triangle : tris) {

            Vector2D a = triangle.a;
            Vector2D b = triangle.b;
            Vector2D c = triangle.c;

            gl.glVertex2d(a.x, a.y);
            gl.glVertex2d(b.x, b.y);
            gl.glVertex2d(c.x, c.y);
        }

        gl.glEnd();


        gl.glColor4ub((byte) colorDebugEdge.getRed(), (byte) colorDebugEdge.getGreen(),
                (byte) colorDebugEdge.getBlue(), (byte) colorDebugEdge.getAlpha());
        gl.glBegin(GL.GL_LINES);
        for (Triangle2D triangle : tris) {

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

    }

    TextRenderer renderer = new TextRenderer(new Font("Arial", Font.PLAIN, 26));

    private void drawCircle(GL2 gl, Color color, Vector2D center, double radius) {
        gl.glColor3ub((byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue());

        int res = 5;

        for (int i = 0; i < 360; i += res) {
            double deg = Math.toRadians(i);

            Vector2D p = center.add(new Vector2D(radius * Math.cos(deg), radius * Math.sin(deg)));
            gl.glVertex2d(p.x, p.y);

            deg = Math.toRadians(i + res);

            p = center.add(new Vector2D(radius * Math.cos(deg), radius * Math.sin(deg)));
            gl.glVertex2d(p.x, p.y);
        }

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

    private Triangle2D findSmallestAngle() {
        double minAngle = Double.MAX_VALUE;
        Triangle2D smallestTri = null;

        List<Triangle2D> tris = delaunayTriangulator.getTriangles();

        for (int i = 0; i < tris.size(); i++) {
            Triangle2D tri = tris.get(i);
            double min = getSmallestAngle(tri);
            if (minAngle > min) {
                minAngle = min;
                smallestTri = tri;
            }
        }

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

    private Triangle2D findLargestArea() {
        double maxArea = Double.MIN_VALUE;
        Triangle2D biggestArea = null;

        List<Triangle2D> tris = delaunayTriangulator.getTriangles();

        for (int i = 0; i < tris.size(); i++) {
            Triangle2D tri = tris.get(i);
            double area = getArea(tri);
            if (maxArea < area) {
                maxArea = area;
                biggestArea = tri;
            }
        }
        return biggestArea;
    }

    private double getArea(Triangle2D tri) {
        Vector2D a = tri.a;
        Vector2D b = tri.b;
        Vector2D c = tri.c;

        return Math.abs(a.sub(b).cross(c.sub(b))) / 2;
    }

    /**
     * Get the ABC angle
     *
     * @param a
     * @param b
     * @param c
     * @return angle in degrees
     */
    private double getAngle(Vector2D a, Vector2D b, Vector2D c) {
        Vector2D ab = a.sub(b);
        Vector2D cb = c.sub(b);
        double dot = ab.dot(cb) / ab.mag() / cb.mag();
        return Math.toDegrees(Math.acos(dot));
    }


    private void updateCalculations() {
        minAngle = findSmallestAngle();
        largestArea = findLargestArea();


        // hull only changes when point added/load (or during refinement)
        delaunayTriangulator.calculateHull();

        // remove invalid fixed edges
        for (Edge2D fixedEdge : (ArrayList<Edge2D>) delaunayTriangulator.fixedEdges.clone()) {
            if (delaunayTriangulator.triangleSoup.findOneTriangleSharing(fixedEdge) == null) {
                delaunayTriangulator.fixedEdges.remove(fixedEdge);
            }
        }
        canvas.display();
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }


    @Override
    public void mousePressed(MouseEvent event) {
        Point p = event.getPoint();
        Vector2D point = new Vector2D(p.x, p.y);
        TriangleSoup soup = delaunayTriangulator.triangleSoup;
        switch (MODE) {
            case SET_EDGE_CONSTRAINT:
                // toggle edge
                Edge2D edge = soup.findNearestEdge(new Vector2D(p.x, p.y));
                delaunayTriangulator.toggleEdge(edge);
                break;
            case INSERT_VERTEX:
                addPoints(point);
                break;
            case SPLIT_EDGE:
                Edge2D edge2split = soup.findNearestEdge(new Vector2D(p.x, p.y));
                delaunayTriangulator.splitEdge(edge2split);
                updateCalculations();
                break;
            case INSERT_TO_CENTER:
                Triangle2D triangle = soup.findContainingTriangle(point);
                if (triangle == null) {
                    break;
                }

                Edge2D encroachedEdge = delaunayTriangulator.insertCircumcenter(triangle);
                if (encroachedEdge != null) {
                    statusText = "New vertex would encroach on " + encroachedEdge;
                } else {
                    statusText = "New vertex inserted";

                }
                break;
        }

        canvas.display();
    }


    /**
     * Adding a point
     *
     * @param p
     */
    private void addPoints(Vector2D p) {
        pointSet.add(p);
        try {
            delaunayTriangulator.triangulate();
        } catch (NotEnoughPointsException e1) {
        }
        updateCalculations();
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
