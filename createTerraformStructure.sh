TF_PROJECT_NAME=terraform

mkdir $TF_PROJECT_NAME

tffiles=('main' 'variables' 'providers' 'versions' 'outputs'); for file in "${tffiles[@]}" ; do touch "$TF_PROJECT_NAME/$file".tf; done
