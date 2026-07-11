# Clean minikube and start it again
minikube delete --all
minikube start

echo "Minikube started. Loading images and to minikube..."
# Load the images into minikube
minikube image load stlab/saga-orchestrator:latest
minikube image load stlab/service-template:latest

echo "Images loaded. Deploying the experiment..."
# Deploy the folder passed as input
kubectl apply -f $1

echo "Experiment deployed. Waiting for pods to be ready..."
# Wait till all the pods are ready
kubectl wait --for=condition=Ready pods --all -A --timeout=300s

echo "All pods are ready. Starting port forwarding..."
# Port forwarding for the orchestrator
nohup kubectl port-forward svc/saga-orchestrator 8080:80 > port-forward.log 2>&1 &
PF_PID=$! # Capture the PID of the background process

echo "Waiting for port forwarding to be established..."
counter=0

# Wait for the port forwarding to be ready
while ! nc -z localhost 8080; do
  sleep 1
  ((counter++))
  
  if [ $counter -ge 30 ]; then
    echo "Port forwarding failed to respond within 30s. Retrying..."
    
    # Kill the old, stalled port-forward process
    kill $PF_PID 2>/dev/null
    
    # Restart the port forwarding command
    nohup kubectl port-forward svc/saga-orchestrator 8080:80 > port-forward.log 2>&1 &
    PF_PID=$! # Capture the new PID
    
    # Reset the counter for the next 30-second window
    counter=0
  fi
done

echo "Port forwarding established successfully!"

echo "Starting the experiment..."
###################
## Generate load ##
###################
k6 run -e REQ_COUNT=5750 load-generation.js

echo "Load generation completed. Fetching results..."
###################
## Save CSV file ##
###################
# Fetch the results from the saga-orchestrator pod
echo "Fetching results from saga-orchestrator..."
POD_NAME=$(kubectl get pods -o custom-columns=":metadata.name" --no-headers | grep saga-orchestrator | head -n 1)

if [ -z "$POD_NAME" ]; then
  echo "Error: saga-orchestrator pod not found!"
  exit 1
fi

# Get the parent directory of $1
PARENT_DIR=$(dirname "$1")

# Define the new target directory
RESULT_DIR="${PARENT_DIR}/../result"

# Create the result directory if it doesn't already exist
mkdir -p "$RESULT_DIR"

FILE_PATH=$(kubectl exec $POD_NAME -- sh -c 'ls data/experiments/*/saga-results.csv | head -n 1')
kubectl cp "$POD_NAME:$FILE_PATH" "$RESULT_DIR/$2.csv"
echo "Results successfully copied to $RESULT_DIR/$2.csv"

# Save orchestrator logs
kubectl logs svc/saga-orchestrator > "$RESULT_DIR/orchestrator.log"