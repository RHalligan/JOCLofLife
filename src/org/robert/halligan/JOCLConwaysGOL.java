package org.robert.halligan;

import static org.jocl.CL.*;

import java.util.Random;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;

import javax.swing.*;

import org.jocl.*;

/**
 * A class that uses a simple OpenCL kernel to simulate Conway's game of life
 */
public class JOCLConwaysGOL
{
    /**
     * Entry point for this sample.
     * 
     * @param args not used
     */
    public static void main(String args[])
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                new JOCLConwaysGOL(1000,1000, 5);
            }
        });
    }
    
    private Random random = new Random();
    
    /**
     * The image which will display the board
     */
    private BufferedImage image;
    
    /**
     * The width and height of the tiles
     */
    int tileSize = 0;
    
    /**
     * The width of the image
     */
    private int sizeX = 0;

    /**
     * The height of the image
     */
    private int sizeY = 0;

    /**
     * The component which is used for rendering the image
     */
    private JComponent imageComponent;
    
    /** 
     * The OpenCL context
     */
    private cl_context context;

    /**
     * The OpenCL command queue
     */
    private cl_command_queue commandQueue;

    /**
     * The OpenCL kernel which will compute the game of life simulation
     */
    private cl_kernel kernel;
    
    private int[] input;
    private cl_mem inputMem;
    private cl_mem outputMem;
    
    /**
     * Creates the JOCLConwaysGOL with the given
     * width and height
     */
    public JOCLConwaysGOL(int width, int height, int tsize) //NOTE:: tsize has to be a multipule of both width and height
    {
        this.sizeX = width;
        this.sizeY = height;
        this.tileSize = tsize;

        // Create the image and the component that will paint the image
        image = new BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_RGB);
        imageComponent = new JPanel()
        {
            private static final long serialVersionUID = 1L;
            public void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                g.drawImage(image, 0,0,this);
            }   
        };
        
        // Initialize the mouse interaction
        initInteraction();

        // Initialize OpenCL
        initCL();

        // Initial image update 
        updateImage();
        
        // Create the main frame
        JFrame frame = new JFrame("JOCL Conways Game of Life");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        imageComponent.setPreferredSize(new Dimension(width, height));
        frame.add(imageComponent, BorderLayout.CENTER);
        frame.pack();
        
        frame.setVisible(true);
    }
    
    /**
     * Initialize OpenCL: Create the context, the command queue
     * and the kernel.
     */
    @SuppressWarnings("deprecation")
	private void initCL()
    {
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_ALL;
        final int deviceIndex = 0;

        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        // Obtain a platform ID
        cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
        
        // Obtain the number of devices for the platform
        int numDevicesArray[] = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];
        
        // Obtain a device ID 
        cl_device_id devices[] = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        cl_device_id device = devices[deviceIndex];

        // Create a context for the selected device
        context = clCreateContext(
            contextProperties, 1, new cl_device_id[]{device}, 
            null, null, null);
        
        // Create a command-queue for the selected device
        commandQueue = 
            clCreateCommandQueue(context, device, 0, null);

        // Program Setup
        String source = readFile("kernels/ConwaysGOL.cl");

        // Create the program
        cl_program cpProgram = clCreateProgramWithSource(context, 1, 
            new String[]{ source }, null, null);

        // Build the program
        clBuildProgram(cpProgram, 0, null, "-cl-mad-enable", null, null);

        // Create the kernel
        kernel = clCreateKernel(cpProgram, "computeConway", null);
        
        // Create the Input data
        initInputMap();
        inputMem = clCreateBuffer(context, CL_MEM_READ_WRITE, 
                input.length * Sizeof.cl_uint, null, null);
    	clEnqueueWriteBuffer(commandQueue, inputMem, true, 0, 
                input.length * Sizeof.cl_uint, Pointer.to(input), 0, null, null);
        //Create the Output Data
    	outputMem = clCreateBuffer(context, CL_MEM_READ_WRITE, 
                input.length * Sizeof.cl_uint, null, null);
    }
    
    /**
     * Helper function which reads the file with the given name and returns 
     * the contents of this file as a String. Will exit the application
     * if the file can not be read.
     * 
     * @param fileName The name of the file to read.
     * @return The contents of the file
     */
    private String readFile(String fileName)
    {
        try
        {
            BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(fileName)));
            StringBuffer sb = new StringBuffer();
            String line = null;
            while (true)
            {
                line = br.readLine();
                if (line == null)
                {
                    break;
                }
                sb.append(line).append("\n");
            }
            br.close();
            return sb.toString();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }
    
    private void initInputMap()
    {
    	input = new int[(sizeX/tileSize)*(sizeY/tileSize)];
    	
    	for (int ii=0; ii < input.length; ii++){
    		if (random.nextBoolean()){
    			input[ii] = 1; //alive
    		}else{
    			input[ii] = 0; //dead
    		}
    	}
    }
    
    
    /**
     * Attach the mouse- and mouse wheel listeners to the glComponent
     * left mouseclick toggles the cells between live and dead
     * any other mouseclick toggles the simulation
     * scrolling the mousewheel progresses the simulation one step at a time
     */
    java.util.Timer timer;
    private void initInteraction()
    {
        imageComponent.addMouseListener(new MouseListener()
        {
            
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON1){
					// find cell location and flip cell
					int x = e.getX()/tileSize;
	                int y = e.getY()/tileSize;
	                
	                clEnqueueReadBuffer(commandQueue, inputMem, CL_TRUE, 0, 
	                        Sizeof.cl_int * input.length, Pointer.to(input), 0, null, null); // get the cells from buffer into java array
	                
	                input[y*(sizeX/tileSize) + x] = input[y*(sizeX/tileSize) + x] ^ 1; // flip the clicked cell
	                
	                clEnqueueWriteBuffer(commandQueue, inputMem, true, 0, 
	                        input.length * Sizeof.cl_uint, Pointer.to(input), 0, null, null);// write back to buffer
	                
	                updateImage();
				}else{
					// toggle the simulation using timers
	                if (timer != null){
	                	timer.cancel();
	                	timer = null;
	                }else{
		                timer = new java.util.Timer();
						timer.schedule(new java.util.TimerTask() {
							@Override
							public void run() {
								updateGame();
							}
						}, 0, 5);
	                }
				}
			}
			
			// Empty methods to complete the interface
			@Override
			public void mouseEntered(MouseEvent arg0) {}
			
			@Override
			public void mouseExited(MouseEvent arg0) {}
			
			@Override
			public void mousePressed(MouseEvent arg0) {
				
			}
			
			@Override
			public void mouseReleased(MouseEvent arg0) {}
            
        });
        
        imageComponent.addMouseWheelListener(new MouseWheelListener()
        {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                updateGame();
            }
        });
    }
    

    /**
     * Execute the kernel function
     */
    private void updateGame()
    {
        // Set how many threads are to be executed
        long globalWorkSize[] = new long[2];
        globalWorkSize[0] = sizeX/tileSize;
        globalWorkSize[1] = sizeY/tileSize;
        
        // Set the parameters for the kernel
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(inputMem));							//input
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(outputMem));						//output
        clSetKernelArg(kernel, 2, Sizeof.cl_uint, Pointer.to(new int[]{sizeX/tileSize}));		//width
        clSetKernelArg(kernel, 3, Sizeof.cl_uint, Pointer.to(new int[]{input.length}));			//size
        
        // Run the kernel
        clEnqueueNDRangeKernel(commandQueue, kernel, 2, null, 
            globalWorkSize, null, 0, null, null);
        
        //swap the input(read) and output(write) buffers 
        cl_mem temp = outputMem;
        outputMem = inputMem;
        inputMem = temp;
        
        updateImage();
    }
    
    private void updateImage()
    {
	    clEnqueueReadBuffer(commandQueue, inputMem, CL_TRUE, 0, 
	    		Sizeof.cl_int * input.length, Pointer.to(input), 0, null, null);
	    
	    
	    //draw the scene
	    Graphics2D g2d = image.createGraphics();
	    g2d.setColor(Color.BLACK);
	    g2d.fillRect(0, 0, sizeX, sizeY);
	    g2d.setColor(Color.WHITE);
	    for (int i = 0; i < input.length; i++){
	    	if (input[i] == 1){
	    		int s = sizeX/tileSize;
	    		g2d.fillRect((i%s)*tileSize, (i/s)*tileSize, tileSize, tileSize);
	    	}
	    }
	    
	    imageComponent.repaint();
	    
    }
    
}
