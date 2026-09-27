package Client;

import common.JobPayload;
import common.WorkerService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.rmi.Naming;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Swing GUI client for the DistriLab distributed system.
 *
 * Connects to any known worker's RMI stub and submits jobs (MAX, PRIMESUM,
 * PRIMECOUNT). The worker-side submitJob() implementation is expected to
 * forward the call to the current coordinator if the connected worker is
 * not the leader, so this client does not need to independently discover
 * who the coordinator is.
 *
 * Supports manual entry or CSV file loading for job data, and runs each
 * submission on a background thread (SwingWorker) so multiple jobs can be
 * in flight concurrently without blocking the UI.
 */
public class clientGUI extends JFrame {

    private JTextField rmiUrlField;
    private JButton connectButton;
    private JLabel connectionStatusLabel;
    private WorkerService connectedWorker;

    private JComboBox<String> jobTypeCombo;
    private CardLayout inputCardLayout;
    private JPanel inputCardPanel;

    // MAX / PRIMECOUNT input (array)
    private JTextArea numbersArea;

    // PRIMESUM input (range)
    private JTextField startField;
    private JTextField endField;

    private JButton loadCsvButton;
    private JButton submitButton;
    private JLabel loadedFileLabel;

    private DefaultTableModel resultsTableModel;
    private JTable resultsTable;

    private final AtomicInteger jobCounter = new AtomicInteger(1);

    private static final String CARD_ARRAY = "ARRAY_INPUT";
    private static final String CARD_RANGE = "RANGE_INPUT";

    public clientGUI() {
        super("DistriLab Client");
        buildUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(760, 560);
        setLocationRelativeTo(null);
    }

    private void buildUI() {
        setLayout(new BorderLayout(8, 8));

        add(buildConnectionPanel(), BorderLayout.NORTH);
        add(buildJobPanel(), BorderLayout.CENTER);
        add(buildResultsPanel(), BorderLayout.SOUTH);
    }

    // ---------------------------------------------------------------
    // Connection panel
    // ---------------------------------------------------------------
    private JPanel buildConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Connection"));

        panel.add(new JLabel("Worker RMI URL:"));
        rmiUrlField = new JTextField("rmi://localhost:1099/Worker_101", 28);
        panel.add(rmiUrlField);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> connectToWorker());
        panel.add(connectButton);

        connectionStatusLabel = new JLabel("Not connected");
        connectionStatusLabel.setForeground(Color.RED);
        panel.add(connectionStatusLabel);

        return panel;
    }

    private void connectToWorker() {
        String url = rmiUrlField.getText().trim();
        try {
            connectedWorker = (WorkerService) Naming.lookup(url);
            int id = connectedWorker.getNodeId();
            connectionStatusLabel.setText("Connected (via Worker " + id + ")");
            connectionStatusLabel.setForeground(new Color(0, 128, 0));
        } catch (Exception ex) {
            connectedWorker = null;
            connectionStatusLabel.setText("Connection failed");
            connectionStatusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this,
                    "Could not connect to worker at:\n" + url + "\n\n" + ex.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------------
    // Job submission panel
    // ---------------------------------------------------------------
    private JPanel buildJobPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Submit Job"));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        topRow.add(new JLabel("Job Type:"));
        jobTypeCombo = new JComboBox<>(new String[]{"MAX", "PRIMESUM", "PRIMECOUNT"});
        jobTypeCombo.addActionListener(e -> updateInputCard());
        topRow.add(jobTypeCombo);

        loadCsvButton = new JButton("Load CSV...");
        loadCsvButton.addActionListener(e -> loadFromCsv());
        topRow.add(loadCsvButton);

        loadedFileLabel = new JLabel(" ");
        topRow.add(loadedFileLabel);

        panel.add(topRow, BorderLayout.NORTH);
        panel.add(buildInputCards(), BorderLayout.CENTER);

        submitButton = new JButton("Submit Job");
        submitButton.addActionListener(e -> onSubmit());
        JPanel submitRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        submitRow.add(submitButton);
        panel.add(submitRow, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildInputCards() {
        inputCardLayout = new CardLayout();
        inputCardPanel = new JPanel(inputCardLayout);

        // Array input card: used for MAX and PRIMECOUNT
        JPanel arrayCard = new JPanel(new BorderLayout(4, 4));
        arrayCard.add(new JLabel("Numbers (comma-separated):"), BorderLayout.NORTH);
        numbersArea = new JTextArea(6, 40);
        numbersArea.setLineWrap(true);
        arrayCard.add(new JScrollPane(numbersArea), BorderLayout.CENTER);
        inputCardPanel.add(arrayCard, CARD_ARRAY);

        // Range input card: used for PRIMESUM
        JPanel rangeCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        rangeCard.add(new JLabel("Start:"));
        startField = new JTextField(10);
        rangeCard.add(startField);
        rangeCard.add(new JLabel("End:"));
        endField = new JTextField(10);
        rangeCard.add(endField);
        inputCardPanel.add(rangeCard, CARD_RANGE);

        return inputCardPanel;
    }

    private void updateInputCard() {
        String jobType = (String) jobTypeCombo.getSelectedItem();
        if ("PRIMESUM".equalsIgnoreCase(jobType)) {
            inputCardLayout.show(inputCardPanel, CARD_RANGE);
        } else {
            inputCardLayout.show(inputCardPanel, CARD_ARRAY);
        }
    }

    // ---------------------------------------------------------------
    // CSV loading
    // ---------------------------------------------------------------
    private void loadFromCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select CSV file");
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        String jobType = (String) jobTypeCombo.getSelectedItem();
        try (BufferedReader reader = new BufferedReader(new FileReader(chooser.getSelectedFile()))) {
            StringBuilder allValues = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    if (allValues.length() > 0) allValues.append(",");
                    allValues.append(line.trim());
                }
            }
            String combined = allValues.toString();

            if ("PRIMESUM".equalsIgnoreCase(jobType)) {
                // Expect exactly two values: start,end
                String[] parts = combined.split(",");
                if (parts.length >= 2) {
                    startField.setText(parts[0].trim());
                    endField.setText(parts[1].trim());
                } else {
                    throw new IllegalArgumentException("PRIMESUM CSV must contain start,end");
                }
            } else {
                numbersArea.setText(combined);
            }

            loadedFileLabel.setText("Loaded: " + chooser.getSelectedFile().getName());

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load CSV:\n" + ex.getMessage(),
                    "CSV Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------------
    // Job submission (background thread via SwingWorker)
    // ---------------------------------------------------------------
    private void onSubmit() {
        if (connectedWorker == null) {
            JOptionPane.showMessageDialog(this, "Connect to a worker first.",
                    "Not Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String jobType = (String) jobTypeCombo.getSelectedItem();
        JobPayload payload;

        try {
            payload = buildPayload(jobType);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Invalid input:\n" + ex.getMessage(),
                    "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int jobId = jobCounter.getAndIncrement();
        int rowIndex = resultsTableModel.getRowCount();
        resultsTableModel.addRow(new Object[]{jobId, jobType, "Running...", ""});

        final WorkerService workerRef = connectedWorker;

        SwingWorker<Object, Void> worker = new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return workerRef.submitJob(jobType, payload);
            }

            @Override
            protected void done() {
                try {
                    Object result = get();
                    resultsTableModel.setValueAt("Done", rowIndex, 2);
                    resultsTableModel.setValueAt(String.valueOf(result), rowIndex, 3);
                } catch (Exception ex) {
                    resultsTableModel.setValueAt("Failed", rowIndex, 2);
                    resultsTableModel.setValueAt(ex.getCause() != null
                            ? ex.getCause().getMessage() : ex.getMessage(), rowIndex, 3);
                }
            }
        };
        worker.execute();
    }

    private JobPayload buildPayload(String jobType) {
        if ("PRIMESUM".equalsIgnoreCase(jobType)) {
            int start = Integer.parseInt(startField.getText().trim());
            int end = Integer.parseInt(endField.getText().trim());
            if (start > end) {
                throw new IllegalArgumentException("Start must be <= End");
            }
            return new JobPayload(start, end);
        } else {
            String raw = numbersArea.getText().trim();
            if (raw.isEmpty()) {
                throw new IllegalArgumentException("Enter at least one number");
            }
            String[] parts = raw.split(",");
            List<Integer> values = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(Integer.parseInt(trimmed));
                }
            }
            int[] arr = new int[values.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = values.get(i);
            return new JobPayload(arr);
        }
    }

    // ---------------------------------------------------------------
    // Results table
    // ---------------------------------------------------------------
    private JPanel buildResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Job Results"));

        resultsTableModel = new DefaultTableModel(
                new Object[]{"Job ID", "Type", "Status", "Result"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        resultsTable = new JTable(resultsTableModel);
        panel.add(new JScrollPane(resultsTable), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(760, 220));

        return panel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            clientGUI gui = new clientGUI();
            gui.setVisible(true);
        });
    }
}
