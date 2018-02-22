# Making a Docker image of GCP

 - build the distribution of the appropriate version of GCP
 - unzip the distribution in this directory and rename the resulting folder to "gcp"
 - docker build -t gatenlp/gcp:version .

# Running GCP under docker

The application you want to run and the input/output directories, report files
and batch definitions need to be mounted into the docker container as a volume.
The easiest way to achieve this is to put everything under one folder and map
that to a path like /control

  - control
    - in
    - out
    - err
    - reports
    - logs
    - application
      - your-app.xgapp and anything it depends on
    - data
      - the data files you want to process
    - output
      - this is where output files will go

You would then design your batch definitions to match this structure:

    <batch id="batch-example" xmlns="http://gate.ac.uk/ns/cloud/batch/1.0">
      <input dir="/control/data" ... />
      <output dir="/control/output" ... />
      <report file="../reports/report-example.xml" />
      <application file="/control/application/your-app.xgapp" />
      
      <documents>
        <documentEnumerator dir="/control/data" ... />
      </documents>
    </batch>

## File ownership

By default the root process in a docker container runs as root, so you probably
want to specify `-u` to `docker run` to make it run as the user who owns the
control directory, otherwise the output files and report will end up owned by
root.

    docker run --rm -it -u `id -u`:`id -g` -v /path/to/control:/control gatenlp/gcp:3.0-alpha1 -d /control
