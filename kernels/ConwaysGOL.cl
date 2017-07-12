/*
 * JOCL - Java bindings for OpenCL
 *
 * Copyright 2009 Marco Hutter - http://www.jocl.org/
 */

// A very simple OpenCL kernel for computing the mandelbrot set
//
// output        : A buffer with sizeX*sizeY elements, storing
//                 the colors as RGB ints
// width         : The width of the buffer

__kernel void computeConway(
    __global uint *input,
    __global uint *output,
    int width,
    int size
    )
{
    int ix = get_global_id(0);
    int iy = get_global_id(1);
    
    
    int ind = ix*width + iy; // the index of the current cell
    int r1 = (ix+1)*width+iy; // the index of the cell directly above
    int r2 = (ix-1)*width+iy; // the index of the cell directly beneath
    
    int count = 0;
    int i;
    for (int y = -1; y < 2; y++){
    	i = ind + y*width;
	    for (int x = -1; x < 2; x++){
	    	int ii = i + x;
	    	if (ii >= 0 && ii < size) count = count + input[ii];
	    }
    }
    
	int isAlive = input[ind];
	count = count - isAlive;
	
	
	if (count == 3){
		output[ind] = 1;
	}else{
		if (count == 2 && isAlive) output[ind] = 1;
		else output[ind] = 0;
	}
}