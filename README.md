# slice-register

This utility registers fMRI data to a reference volume, one slice at a time.

## Installing

Choose your executable file based on your preferred platform:

[Java](https://gitlab.com/geoffmay/slice-register/-/raw/main/java/slice-register_1.0.0.jar?inline=false)

[Windows](https://gitlab.com/geoffmay/slice-register/-/raw/main/java/slice-register_1.0.0.exe?inline=false)

[Linux](https://gitlab.com/geoffmay/slice-register/-/raw/main/java/slice-register_1.0.0_linux?inline=false)

The executables are self-contained and shouldn't require anything special. Linux users may need to add execution permission:

`chmod +x slice-register_1.0.0_linux`

## Running the program from the command line

The program takes a nifti file (.nii or .nii.gz extension) as input, and outputs a series of .json files.

### Java (requires 1.8 or higher)

<b>Step 1:</b> generate a registration schedule

`java -jar slice-register_1.0.0.jar generate -i {input_file.nii(.gz)} -o {schedule_file.json}`

This creates a .json file that can be edited to change options for registration.

<b>Step 2:</b> perform registration on each desired source/reference pair

`java -jar slice-register_1.0.0.jar register -i {schedule_file.json} -o {output_folder} -s {source_volume_number} -r {reference_volume_number}`

{schedule_file.json} should point to the schedule file you created/edited in the previous step.

{source_volume_number} and {reference_volume_number} are integers, the first volume is 0.

The registration results will be saved to {registration_folder}/{input_file}_vol-{ref}-{src}.json

The individual steps of the gradient descent process will be saved to {registration_folder}/logs

<b>Step 3:</b> (in process) interpolate registered slices into a uniformly distributed output nifti file

The current focus is on the physical causes of the translations that occur in the scanner and how different scanners behave differently.


### Sharding

You can optionally split the file into volumes:

`java -jar slice-register_1.0.0.jar shard -i <input_file> -o <output_folder>`

This will create a "shard" folder that contains each volume, which allows volumes to be processed separately. This can save memory and bandwidth for processing on multiple machines.


### Native executables

The .jar file was compiled using GraalVM to be run natively in Windows (slice-register_1.0.0.exe) or Linux/Mac (slice-register_1.0.0) environments. Just replace the "java -jar slice-register_1.0.0.jar" part of each command with the path to the executable, e.g.:

`slice-register_1.0.0.exe generate -i <input_file.nii(.gz)> -o <schedule_file.json>`

`slice-register_1.0.0.exe register -i <schedule_file.json> -o <output_folder> -s <source_volume_number> -r <reference_volume_number>`

`slice-register_1.0.0.exe shard -i <input_file> -o <output_folder>`


## Program logic overview

The registration process itself occurs in a series of steps like gradient descent. For each iteration, a cost function is calculated for a series of probes, and which probe has the lowest cost determines what happens next.

If the lowest cost is somewhere in the middle of the probes, the span of the probe array for the next iteration is narrowed.

If the lowest cost is on the edge of the probe array, the span of the probe array for the next iteration is widened.

The process continues until some stopping criteria is met:
- the cost stops descending for a number of iterations
- the span of the probe array remains sufficiently narrorw for a number of iterations
- some maximum number of iterations is reached

The cost function used is the mean squared difference between the source data and the transformed reference data.

All data is sampled using coordinates, with cubic interpolation. There is no slice-time correction for data during registration.


## Schedule configuration

Each schedule is an array of scales, and each scale can be tweaked individually. The result of registration at one scale is meant to be fed as a starting point into the next scale in the sequence.

      "degreesOfFreedom":  
      6-> rotate and translate 
      3-> translate only
      2-> translate x and y only
      1-> translate y only

      "doSlices": 
      false -> register source volume to reference volume
      true -> register source slice to reference volume

      "adjustmentStart": 0.01
      the fraction of the total range that we span with the probes
      
      "maxScaleStart": 1.5,
      determines how the probe array is scaled for the next iteration when the lowest cost is at the edge of the probe array
      
      "minScaleStart": 0.5,
      determines how the probe array is scaled for the next iteration when the lowest cost is in the center of the probe array
      
      "stopThresholdStart": 1.0E-12,
      the reduction in cost we consider too insignificant to keep going

      "maxStagnantIterations": 30,
      the number of times we have to hit threshold criteria in order to stop gradient descent
      
      "tweakStopThreshold": true,
      
      "doFrequencyScaling": false,
      no longer used
      
      "downSampleCount": 1,
      the number of times the nifti file is halved in each spatial dimension, for faster volume-to-volume comparisons
      
      "isParabolic":  
      false -> creates an array of probes and calculates the cost for each of them, choosing the lowest
      true -> creates three probes and fits a parabola to their costs to compute the location of the expected minimum

These are all hyperparameters that could theoretically be tuned using gradient descent, using some combination of time to compute and accuracy in their own cost function.

There are additional settings in the program that can be elevated to schedule configuration in the future.

## License
This project is public domain, use it however you like.
