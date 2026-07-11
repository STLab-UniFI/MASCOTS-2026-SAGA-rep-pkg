#!/bin/bash

# Loop through all subdirectories in workflows_experimentation that contain a 'yamls' folder
for yaml_dir in ./analysis-results/*/yamls/; do

    folder_name=$(basename "$(dirname "$yaml_dir")")
    echo "Workflow name: $folder_name"
    
    # Check if the directory actually exists (handles cases where glob doesn't match)
    if [ -d "$yaml_dir" ]; then
        echo "Running experiment for: $yaml_dir"
        
        # 1. Get the parent directory (stripping the trailing slash first)
        parent_dir=$(dirname "${yaml_dir%/}")
        
        # Define the log file path
        log_file="$parent_dir/experiment.log"
        
        echo "Logs will be saved to: $log_file"
        
        # 2. Call your script and redirect standard output and errors to the log file
        ./single_experiment.sh "$yaml_dir" "$folder_name" > "$log_file" 2>&1
        
    else
        echo "Warning: Directory $yaml_dir not found."
    fi

done

echo "All experiments finished!"