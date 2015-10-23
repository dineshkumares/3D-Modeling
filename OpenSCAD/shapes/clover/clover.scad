
use <../heart/heart.scad>;



module flatClover(sideHeartRotation = 100)
{
	union () 
	{ 
		rotate ([0, 0, sideHeartRotation])
		translate ([0,14,0])
		raisedHeart();
		
		rotate ([0, 0, 0])
		translate ([0,14,0])
		raisedHeart();
		
		rotate ([0, 0, -sideHeartRotation])
		translate ([0, 14, 0])
		raisedHeart();
		
//		color ("red")
		translate([-3.5,-30,0])
		cube (size = [7, 40, 15]);
	}
}

module raisedClover()
{
//	linear_extrude (height = 10)
	flatClover();
}

raisedClover();
