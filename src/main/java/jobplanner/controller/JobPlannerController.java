package jobplanner.controller;

import jobplanner.model.Filters;
import jobplanner.model.formatters.DataFormatter;
import jobplanner.model.formatters.Formats;
import jobplanner.model.models.JobPostModel;
import jobplanner.model.models.SavedJobModel;
import jobplanner.model.models.IJobPostModel.JobRecord;
import jobplanner.model.models.ISavedJobModel;
import jobplanner.model.types.JobCategory;
import jobplanner.view.JobPlannerGUI;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The JobPlannerController class is responsible for handling the interactions
 * between the view (GUI) and the model in the job planning application.
 * It processes user input from the GUI, applies filters to job data, and updates the view.
 */
public class JobPlannerController implements ActionListener {
    /** The main GUI view. */
    private JobPlannerGUI view;

    /** The filter logic. */
    private Filters filters;

    /** The model containing job data. */
    private JobPostModel model;

    /** The model containing saved jobs. */
    private ISavedJobModel savedJobs;

    /**
     * Constructs a JobPlannerController with the specified model and view.
     * Sets up action listeners for the view components.
     *
     * @param model the data model containing job records
     * @param view  the view component of the MVC architecture
     */
    public JobPlannerController(JobPostModel model, JobPlannerGUI view) {
        this.view = view;
        this.filters = new Filters();
        this.model = model;
        this.savedJobs = SavedJobModel.loadFromJson();
        view.setListeners(this);
    }

    /**
     * Starts the application by making the main GUI window visible.
     */
    public void start() {
        SwingUtilities.invokeLater(() -> view.setVisible(true));
    }

    /**
     * Handles the actions performed in the GUI.
     * It processes button clicks and other user actions.
     *
     * @param e the event generated by user actions
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand()) {
            case "Apply Filter":
                applyFilters();
                break;
            case "Reset Filter":
                resetFilters();
                break;
            case "Show Saved Jobs":
                showSavedJobs();
                break;
            case "Export as CSV":
                exportList(Formats.CSV);
                break;
            case "Export as TXT":
                exportList(Formats.PRETTY);
                break;
            default:
                break;
        }
    }

    /**
     * Exports the list of games to a file, either CSV or TXT format.
     * 
     * @param format the format to export the list as.
     */
    private void exportList(Formats format) {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showSaveDialog(view);
        if (result == JFileChooser.APPROVE_OPTION) {
            String filename = fileChooser.getSelectedFile().getAbsolutePath();
            try {
                DataFormatter.write(savedJobs.getSavedJobs(), format, new FileOutputStream(filename));
            } catch (Exception e) {
                view.showErrorDialog("Error exporting file: " + e.getMessage());
            }
        }
    }

    /**
     * Applies the filters specified by the user through the FilterPanel.
     * Filters the job records based on the selected criteria and updates the view.
     */
    private void applyFilters() {
        // Retrieve filter criteria from the filter panel
        String selectedCountry = view.getFilterPanel().getSelectedCountry();
        String selectedCategory = view.getFilterPanel().getSelectedCategory();
        String company = view.getFilterPanel().getCompany();
        double minSalary = parseDouble(view.getFilterPanel().getMinSalary());
        double maxSalary = parseDouble(view.getFilterPanel().getMaxSalary());
        List<String> roleTypes = view.getFilterPanel().getSelectedRoleTypes();
        String dateFilter = view.getFilterPanel().getDateFilter();

        // List to hold filter predicates
        List<Predicate<JobRecord>> predicates = new ArrayList<>();

        // Add predicates based on user input
        if (selectedCountry != null && !selectedCountry.equals("Select")) {
            predicates.add(filters.byCountry(selectedCountry));
        }
        if (selectedCategory != null && !selectedCategory.equals("Select")) {
            predicates.add(filters.byCategory(JobCategory.fromString(selectedCategory)));
        }
        if (company != null && !company.isEmpty()) {
            predicates.add(filters.byCompany(company));
        }
        if (!Double.isNaN(minSalary) && !Double.isNaN(maxSalary)) {
            predicates.add(filters.bySalaryRange(minSalary, maxSalary));
        }
        if (!roleTypes.isEmpty()) {
            predicates.add(filters.byRoleType(roleTypes));
        }

        // Handle date filter
        addDateFilterPredicate(predicates, dateFilter);

        // Get jobs and apply filters
        List<JobRecord> jobs = model.getJobs();
        List<JobRecord> filteredJobs = filters.applyFilters(jobs, predicates);
        updateJobList(filteredJobs);
    }

    /**
     * Adds a date filter predicate based on the selected date range.
     *
     * @param predicates the list of predicates to add to
     * @param dateFilter the selected date range filter
     */
    private void addDateFilterPredicate(List<Predicate<JobRecord>> predicates, String dateFilter) {
        if (!dateFilter.equals("Select")) {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = null;

            // Determine date range based on selection
            switch (dateFilter) {
                case "Past week":
                    startDate = endDate.minus(1, ChronoUnit.WEEKS);
                    break;
                case "Past month":
                    startDate = endDate.minus(1, ChronoUnit.MONTHS);
                    break;
                case "Today":
                    startDate = endDate;
                    break;
                default:
                    break;
            }

            if (startDate != null) {
                predicates.add(filters.byDatePosted(startDate, endDate));
            }
        }
    }

    /**
     * Resets the filters in the FilterPanel to their default values and updates the view.
     */
    private void resetFilters() {
        view.getFilterPanel().reset();
        applyFilters();
    }

    /**
     * Shows the saved jobs in a separate window.
     */
    private void showSavedJobs() {
        // Get selected jobs from the table
        List<JobRecord> selectedJobs = view.getJobListPanel().getJobTableModel().getSelectedJobs();

        // Set saved job list
        savedJobs.setSavedJobs(selectedJobs);

        // Display the saved job list
        view.showSavedJobsPanel(selectedJobs);
    }



    /**
     * Parses a string to a double value. Returns NaN if the parsing fails.
     *
     * @param value the string to parse
     * @return the parsed double value or NaN if parsing fails
     */
    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * Updates the job list in the view with the provided job records.
     *
     * @param jobs the list of job records to display
     */
    public void updateJobList(List<JobRecord> jobs) {
        view.getJobListPanel().getJobTableModel().setJobs(jobs);
    }
}
