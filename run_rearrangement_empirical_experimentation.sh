#!/bin/bash

for graph_dir in ./refactoring-results/*; do
    
    # Ensure it's actually a directory before proceeding
    if [ -d "$graph_dir" ]; then
        echo "Processing directory: $graph_dir"
        
        # 1. Create the 'results' directory inside the current graph folder
        results_dir="$graph_dir/result"
        mkdir -p "$results_dir"
        
        # Path to the yamls directory
        yaml_base_dir="$graph_dir/yamls"
        
        # Check if the 'yamls' directory actually exists
        if [ -d "$yaml_base_dir" ]; then
            
            # 2. Iterate through the subfolders inside 'yamls' (fail-first, fail-last, etc.)
            for yaml_sub in "$yaml_base_dir"/*; do
                if [ -d "$yaml_sub" ]; then
                    
                    # Extract just the folder name (e.g., 'fail-first')
                    sub_name=$(basename "$yaml_sub")

                    if [[ "$sub_name" != "fail-first" && "$sub_name" != "fail-last" && "$sub_name" != "sequential" ]]; then
                        echo "  [Info] Skipping folder: $sub_name (not a recognized strategy)"
                        continue
                    fi
                    
                    echo "  -> Running script for strategy: $sub_name"
                    echo "     YAML path: $yaml_sub"
                    log_file="$results_dir/${sub_name}.log"
                    echo "     Logs will be saved to: $log_file"
                    
                    # 3. Launch your target script and save output to the results folder
                    # CHANGE './your_target_script.sh' to whatever command you actually run
                    ./single_experiment.sh "$yaml_sub" "$sub_name" > "$log_file" 2>&1
                    
                fi
            done
        else
            echo "  [Warning] 'yamls' folder not found in $graph_dir"
        fi
        
        echo "--------------------------------------------------"
    fi
done

echo "All tasks completed!"