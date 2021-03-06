package pl.polsl.Szymon.Bartnik.servlets;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import pl.polsl.Szymon.Bartnik.controller.CalculatorController;
import pl.polsl.Szymon.Bartnik.helpers.DerbyUtils;
import pl.polsl.Szymon.Bartnik.models.ConversionResult;
import pl.polsl.Szymon.Bartnik.models.Result;
import pl.polsl.Szymon.Bartnik.models.exceptions.NegativeNumberException;

/**
 * Api servlet which is accessible from /api link and correct request
 * to this endpoint returns computed number. 
 * 
 * @author Szymon Bartnik (grupa 2)
 * @version 1.0
 */
@WebServlet("/api")
public class ApiServlet extends HttpServlet {    
    
    /** Represents DB connection */
    private Connection connection;
    
    /**
     * Initialization method invoked when creating instance of the class.
     */
    @Override
    public void init(){
        
        connection = DerbyUtils.connectToDatabase(getServletContext());
    }
    
    /**
     * Handling reports with GET method
     * 
     * @param req Request
     * @param resp Respond to a question
     * @exception ServletException if any servlet exception occured
     * @exception IOException if any IOException occured
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        
        // Get action parameter identifying which action we are about to handle.
        String action = req.getParameter("action");
        
        // If user invoking request is not logged, redirect to the logging in page.
        if(!checkCredentials(req, resp)){
            resp.sendRedirect("index.html");
            return;
        }

        // If action parameter was not specified, inform about the problem.
        if(action == null){
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "You should specify an action context!");
            return;
        }
        
        // Recognises which action we are about to perform and do it
        recognizeAndPerformAction(req, resp, action);
    }
    
    /**
     * Handling reports with POST method
     * 
     * @param req Request
     * @param resp Respond to a question
     * @exception ServletException if any servlet exception occured
     * @exception IOException if any IOException occured
     */
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }

    /**
     * Computes the number in other numeral system and sends JSON result.
     * 
     * @param req Request
     * @param resp Respond to a question
     * @throws IOException if any IOException occured
     * @throws NumberFormatException if in any step of conversion occured an error connected
     * with wrong format of converted number.
     * @throws NullPointerException if passed null argument
     * @throws NegativeNumberException if passed negative number
     */
    private void computeNumber(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, NumberFormatException, NullPointerException, NegativeNumberException {
        
        // Gets parameters neccessary to compute new number
        String fromNumeralSystem = req.getParameter("from");
        String toNumeralSystem   = req.getParameter("to");
        String numberToConvert   = req.getParameter("number");
        
        // Checks if every parameter is specified and sends error code if not.
        if(fromNumeralSystem == null ||
           toNumeralSystem == null ||
           numberToConvert == null){
            
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not all parameters specified!");
            return;
        }
        
        // Convert parameters to the object array.
        String[] invokeParamters = new String[] { fromNumeralSystem, toNumeralSystem };
        
        // Create calculator controller using specified invoke parameters.
        CalculatorController controller = new CalculatorController(invokeParamters);
        
        // Compute the result 
        ConversionResult result = controller.convertNumber(numberToConvert);

        // Create and send a response.
        PrintWriter out = resp.getWriter();
        out.write(result.getOutputNumber());
    }

    /**
     * Checks if user performing the action on the servlet is authorized.
     * 
     * @param req Request
     * @param resp Respond to a question
     * @return if credentials was fulfilled
     */
    private boolean checkCredentials(HttpServletRequest req, HttpServletResponse resp) {
        
        return req.getSession().getAttribute("user") != null;
    }

    /**
     * Recognizes action to perform in this API servlet and performs the action.
     * 
     * @param req Request
     * @param resp Respond to a question
     * @param action Action to recognize and perform
     * @throws IOException if any IOException occured
     */
    private void recognizeAndPerformAction(HttpServletRequest req, HttpServletResponse resp, String action) 
            throws IOException {
        
        switch(action){
            // If we were about to invoke 'computeNumber' action
            case "computeNumber":
                try {
                    computeNumber(req, resp);
                } catch (NumberFormatException | NullPointerException | NegativeNumberException ex) {
                    // Inform about caller about error if any occured
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
                }
                break;
            case "getResults":
                try{
                    getResults(req, resp);
                } catch (Exception ex) {
                    // Inform about caller about error if any occured
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                }
                break;
            case "saveResult":
                try{
                    saveResult(req, resp);
                } catch (Exception ex) {
                    // Inform about caller about error if any occured
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                }
                break;
                
            default:
                // If action parameter was not recognized.
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action!");
        }
    }

    /**
     * Saves result passed in JSON by parameter in request.
     * 
     * @param req Request
     * @param resp Respond to a question
     * @exception IOException if any IOException occured
     */
    private void saveResult(HttpServletRequest req, HttpServletResponse resp) 
            throws IOException {
        
        // Gets user ID for which save the computation result.
        int userId = (int)req.getSession().getAttribute("userid");
        // Get JSON content of the request.
        String param = req.getParameter("content");
        
        try {
            // Deserialize result to save from JSON string.
            Result result = new Gson().fromJson(param, Result.class);
            // Validate deserialized result object.
            result.validateAfterDeserialization();
            
            Statement statement = connection.createStatement();
            // Execute insert result to Results table.
            statement.executeUpdate("INSERT INTO Results(userid, fromnumeralsystem, "
                    + "tonumeralsystem, numbertoconvert, convertednumber) VALUES "
                    + "(" + userId
                    + ", '" + result.getFromNumeralSystem()
                    + "', '" + result.getToNumeralSystem()
                    + "', '" + result.getNumberToConvert()
                    + "', '" + result.getConvertedNumber() + "')");
            
            // Send empty response (OK).
            PrintWriter out = resp.getWriter();
            out.write("{}");
        } catch (SQLException ex) {
            // Inform about caller about error if any occured
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
        } catch (JsonSyntaxException | IllegalArgumentException | IOException ex){
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } 
    }

    /**
     * Gets all results of current user from DB.
     * 
     * @param req Request
     * @param resp Respond to a question
     * @exception IOException if any IOException occured
     */
    private void getResults(HttpServletRequest req, HttpServletResponse resp) 
            throws IOException {
        
        // Gets user ID for which save the computation result.
        int userId = (int)req.getSession().getAttribute("userid");
        
        LinkedList<Result> results = new LinkedList<>();
        
        try {
            Statement statement = connection.createStatement();
            
            // Query all results of current user from DB.
            ResultSet rs = statement.executeQuery("SELECT * FROM Results WHERE userid = " + userId);
            
            // Iterate result set and add every to the results list.
            while(rs.next()){
                results.add(new Result(rs));
            }
            
            rs.close();
            
            // Create and send a response with results of current user.
            PrintWriter out = resp.getWriter();
            out.write(new Gson().toJson(results));
            
        } catch (SQLException ex) {
            // Inform about caller about error if any occured
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }
    
    /**
     * Cleans up the servlet instance and closes DB connection.
     */
    @Override
    public void destroy(){
        
        if(connection == null){
            return;
        } 
        
        try {
            connection.close();
        } catch (SQLException ex) {
            System.err.println("SQL exception: " + ex.getMessage());
        }
        super.destroy();
    }
}
