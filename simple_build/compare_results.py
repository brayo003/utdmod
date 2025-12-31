import csv
import math

def read_csv(filename):
    ticks = []
    values = []
    with open(filename, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            ticks.append(int(row['tick']))
            values.append(float(row['x']))
    return ticks, values

def compare_results():
    java_ticks, java_values = read_csv('utd_server_trace.csv')
    py_ticks, py_values = read_csv('python_reference.csv')
    
    print(f"Java simulation: {len(java_values)} ticks")
    print(f"Python simulation: {len(py_values)} ticks")
    print()
    
    print("Final values:")
    print(f"Java: {java_values[-1]:.10f}")
    print(f"Python: {py_values[-1]:.10f}")
    print()
    
    print("Peak values:")
    print(f"Java: {max(java_values):.10f}")
    print(f"Python: {max(py_values):.10f}")
    print()
    
    print("Minimum values:")
    print(f"Java: {min(java_values):.10f}")
    print(f"Python: {min(py_values):.10f}")
    print()
    
    # Calculate differences
    differences = []
    for i in range(len(java_values)):
        diff = abs(java_values[i] - py_values[i])
        differences.append(diff)
    
    print("Difference analysis:")
    print(f"Mean absolute difference: {sum(differences)/len(differences):.10f}")
    print(f"Max absolute difference: {max(differences):.10f}")
    print(f"Final difference: {differences[-1]:.10f}")
    
    # Check if results are identical (within floating point precision)
    tolerance = 1e-10
    identical = all(diff < tolerance for diff in differences)
    print(f"Results identical within {tolerance}: {identical}")

if __name__ == "__main__":
    compare_results()
