package Localization;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MultiphoneLocalization extends LocalizationLUT
{
	private static Double [] getX(String Model)
	{
		try 
		{
			return getX(null, null, Model, null);
		} 
		catch (ClassNotFoundException | SQLException e) 
		{
			e.printStackTrace();
		}
		return null;
	}
	
	private static Double [] getY(String Model)
	{
		try 
		{
			return getY(null, null, Model, null);
		} 
		catch (ClassNotFoundException | SQLException e) 
		{
			e.printStackTrace();
		}
		return null;
	}
	
	public static Double [] getX(String OS, String Device, String Model, String Product) 
			throws ClassNotFoundException, SQLException
	{
		Double [] X = null;
		ArrayList<Double> x = new ArrayList<Double>();

		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);
		// execute the query, and get a java result set
		/*
		select distinct Xcoordinate, Ycoordinate from fiu.trainingpoints Order By Xcoordinate ASC;
		 */
		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery(
				"select distinct Xcoordinate, Ycoordinate " +
				"from " + DB + "." + TRAININGDATA + " " +
				"Where model='" + Model + "' " +
				"Order By Xcoordinate ASC;");
		while (rs.next())
		{
			x.add(rs.getDouble("Xcoordinate"));
		}
		X = x.toArray(new Double[x.size()]);
		return X;
	}
	
	public static Double [] getY(String OS, String Device, String Model, String Product) 
			throws ClassNotFoundException, SQLException
	{
		Double [] Y = null;
		ArrayList<Double> y = new ArrayList<Double>();
	
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);

		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery(
				"select distinct Xcoordinate, Ycoordinate " +
				"from " + DB + "." + TRAININGDATA + " " +
				"Where model='" + Model + "' " + " " +
				"Order By Xcoordinate ASC;");
		while (rs.next())
		{
			y.add(rs.getDouble("Ycoordinate"));
		}
		Y = y.toArray(new Double[y.size()]);	
		return Y;
	}
	
	// Modify to create ONLY one table per device
	public static boolean createTables()
	{
		String [] ColumnNames = getColumnMAC();
		
		try
		{
			Class.forName(myDriver);
			System.out.println("Connecting to a local database...");
			Connection conn = DriverManager.getConnection(URL, username, password);
			Statement stmt = conn.createStatement();
			
			// GET ALL UNIQUE PHONES!
			String [] all_phones = getPhones();
			
			for(String phone: all_phones)
			{
				String table_name = phone.replace(" ", "");
				table_name = table_name.replace("-", "");
				
				String sql =
						"CREATE TABLE " + DB + "." + table_name +
						"("
						+ "ID INTEGER not NULL, "
						+ " Xcoordinate DOUBLE not NULL, "
						+ " Ycoordinate DOUBLE not NULL, ";
				String add = "";
				for (int i = 0; i < Distance.VECTOR_SIZE; i++)
				{
					System.out.println(ColumnNames[i]);
					System.out.println(makeColumnName(ColumnNames[i]));
					add += makeColumnName(ColumnNames[i]) + " INTEGER not NULL,";
				}
				
				sql += add;
				sql +=" PRIMARY KEY (ID));"; 
				System.out.println(sql);
				stmt.executeUpdate(sql);
			}
			return true;
		}
		catch(SQLException se)
		{
			se.printStackTrace();
			return false;
		}
		catch(ClassNotFoundException cnf)
		{
			System.err.println("SQL Exception caught: createTables()");
			return false;
		}
	}
	
	private static String [] getPhones()
	{
		String [] phones = null;
		List<String> phone = new ArrayList<String>();
		try
		{
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);

			Statement st = conn.createStatement();
			ResultSet rs = st.executeQuery(
					"select distinct Model " +
					"from " + DB + "." + TRAININGDATA);
			while (rs.next())
			{
				phone.add(rs.getString("Model"));
			}
			phones = phone.toArray(new String[phone.size()]);	
		}
		catch(SQLException se)
		{
			se.printStackTrace();
		}
		catch(ClassNotFoundException cnf)
		{
			System.err.println("SQL Exception caught: createTables()");
		}
		return phones;
	}
	
	public static boolean UpdatePlainLUT()
	{
		try
		{
			// Init
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);
			
			// GET ALL UNIQUE PHONES!
			String [] all_phones = getPhones();
			
			for(String phone: all_phones)
			{
				// Acquire All Data to Create Plain Text Lookup Table	
				Double [] X = getX(phone);
				Double [] Y = getY(phone);
				String [] CommonMac = getColumnMAC();
				int [][] Pinsert = new int [X.length][Distance.VECTOR_SIZE];
				
				Statement Plainst; 
				String getRSS;
				ResultSet RSS;
				for (int x = 0; x < X.length; x++)
				{
					/*
			 		select RSS FROM TRAININGDATA
					WHERE Xcoordinate = 227.761 
					AND YCoordinate = 1095.73 
					AND MACADDRESS = '84:1b:5e:4b:80:e2';
					 */
					
					for (int currentCol = 0; currentCol < Distance.VECTOR_SIZE; currentCol++)
					{
						getRSS = "SELECT RSS FROM " + DB + "." + TRAININGDATA
								+ " WHERE Xcoordinate = "
								+ X[x]
								+ " AND Ycoordinate = "
								+ Y[x]
								+ " AND MACADDRESS = '"
								+ CommonMac[currentCol] + "'"
								+ " AND MODEL = '" + phone + "' "
								+ ";";
									
						Plainst = conn.createStatement();
						RSS = Plainst.executeQuery(getRSS);
						while (RSS.next())
						{
							Pinsert [x][currentCol] = RSS.getInt("RSS");
						}
						
						//CHECK IF I GOT A NULL!
						if (Pinsert[x][currentCol] == 0)
						{
							Pinsert[x][currentCol] = Distance.v_c;
						}
						Plainst.close();		
					}
				}
				
				// -----------------Place data----------------------------------------
				String append = "";
				for (int i = 0; i < Distance.VECTOR_SIZE; i++)
				{
					append += " ?,";
				}
				//Remove the Extra , at the end!!
				append = append.substring(0, append.length() - 1);
				append += ");";
				
				//The Insert Statement for Plain Text
				String table = phone.replace(" ", "");
				table = table.replace("-", "");
				
				String PlainQuery = "insert into " + DB + "." + table
				+ " values (?, ?, ?," + append;
				
				PreparedStatement Plain;

				for (int PrimaryKey = 0; PrimaryKey < X.length; PrimaryKey++)
				{
					if(isNullTuple(Pinsert[PrimaryKey]))
					{
						continue;
					}
					Plain = conn.prepareStatement(PlainQuery);
					
					//Fill up the PlainText Table Part 1
					Plain.setInt (1, PrimaryKey + 1);
					Plain.setDouble(2, X[PrimaryKey]);
					Plain.setDouble(3, Y[PrimaryKey]);
					
					for (int j = 0; j < Distance.VECTOR_SIZE;j++)
					{
						Plain.setInt((j + 4), Pinsert[PrimaryKey][j]);
					}
					Plain.execute();
					Plain.close();
				}
				
				//DONT FORGET TO COMMIT!!!
				Statement commit = conn.createStatement();
				commit.executeQuery("commit;");
				conn.close();	
			}
			return true;
		}
		catch(SQLException | ClassNotFoundException cnf)
		{
			cnf.printStackTrace();
			return false;
		}
	}
	
	public static void getPlainLookup(ArrayList<Long[]> SQLData, ArrayList<Double[]> coordinates, String [] phone_data) 
			throws ClassNotFoundException, SQLException
	{
		
		Class.forName(myDriver);
		Connection conn = DriverManager.getConnection(URL, username, password);
		Statement st = conn.createStatement();
		
		String table = phone_data[2].replace(" ", "");
		table = table.replace("-", "");
		
		ResultSet rs = st.executeQuery(""
				+ "select * from " + DB + "." + table
				+ " Order By Xcoordinate ASC;");
		
		while(rs.next())
		{
			Long [] RSS = new Long [Distance.VECTOR_SIZE];
			Double [] Location = new Double [2];
			Location[0] = rs.getDouble("Xcoordinate");	// 2
			Location[1] = rs.getDouble("Ycoordinate");	// 3
			// Start with 4....
			for (int i = 0; i < Distance.VECTOR_SIZE; i++)
			{
				RSS[i] = (long) rs.getInt(i + 4);
			}
			SQLData.add(RSS);
			coordinates.add(Location);
		}
	}
	
	public static void printLUT()
	{
		String [] ColumnMac = LocalizationLUT.getColumnMAC();
		String header = "Xcoordinate,Ycoordiante,";
		for(int i = 0; i < Distance.VECTOR_SIZE; i++)
		{
			if(i == Distance.VECTOR_SIZE - 1)
			{
				header += ColumnMac[i];
			}
			else
			{
				header += ColumnMac[i] + ",";
			}
		}
		
		try
		{
			Class.forName(myDriver);
			Connection conn = DriverManager.getConnection(URL, username, password);

			String [] phones = getPhones();
			
			for(String phone: phones)
			{
				String table = phone.replace(" ", "");
				table = table.replace("-", "");
				
				String Q3 = "SELECT * FROM " + DB + "." + table;
				String PlainCSV = "./" + phone + "_LUT.csv";
				
				// create the java statement
				Statement stTwo = conn.createStatement();
				
				// execute the query, and get a java result set
				ResultSet PlainResult = stTwo.executeQuery(Q3);
				ResultSetMetaData meta = PlainResult.getMetaData();

				PrintWriter WritePlain = new PrintWriter(
						new BufferedWriter(
								new OutputStreamWriter(
										new FileOutputStream(PlainCSV))));
				
				WritePlain.println(header);
		
				String tuple = "";
				
				while(PlainResult.next())
				{
					// Skip ID, 1
					tuple += PlainResult.getDouble(2) + ",";
					tuple += PlainResult.getDouble(3)  + ",";
					for (int i = 0; i < Distance.VECTOR_SIZE; i++)
					{
						String name = meta.getColumnName(i+4);
						tuple += PlainResult.getInt(name)  + ",";
					}
					// Delete extra ,
					tuple = tuple.substring(0, tuple.length() - 1);
					WritePlain.println(tuple);
					tuple = "";
				}
				WritePlain.close();
			}
		}
		catch(IOException | SQLException | ClassNotFoundException cnf)
		{
			cnf.printStackTrace();
		}
	}
}
