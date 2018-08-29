#pragma version(1)
#pragma rs java_package_name(com.nvision.facetracker)
#pragma rs_fp_relaxed


rs_allocation gIn;
int width;
int height;
int degree;

uchar4 __attribute__((kernel)) rotate(uint32_t x, uint32_t y) {
    uchar4 res4;
    switch(degree)
    {
        case 0: //flip horiz
        {
            const uchar4 *element = rsGetElementAt(gIn, width-x, y);
            float4 color = rsUnpackColor8888(*element);
            float4 output = {color.r, color.g, color.b, 0xFF};
            res4 = rsPackColorTo8888(output);
        }
        break;
        case 180: //flip vert
        {
            const uchar4 *element = rsGetElementAt(gIn, x, height-y);
            float4 color = rsUnpackColor8888(*element);
            float4 output = {color.r, color.g, color.b, 0xFF};
            res4 = rsPackColorTo8888(output);
        }
        break;
        case 270:
        {
            const uchar4 *element = rsGetElementAt(gIn, width-y, x);
            float4 color = rsUnpackColor8888(*element);
            float4 output = {color.r, color.g, color.b, 0xFF};
            res4 = rsPackColorTo8888(output);
        }
        break;
        case 90:
        {
            const uchar4 *element = rsGetElementAt(gIn, y, height-x);
            float4 color = rsUnpackColor8888(*element);
            float4 output = {color.r, color.g, color.b, 0xFF};
            res4 = rsPackColorTo8888(output);
        }
        break;
        default:
        break;
    }
    return res4;
}

void init() {
	rsDebug("init Called ", rsUptimeMillis());
}



int root() {

	rsDebug("root Called ", rsUptimeMillis());
    return 1;
}