import sys
import os

module_path = os.path.abspath(
    os.path.join(
        os.path.dirname(
            os.path.realpath(__file__)), "src"))

if module_path not in sys.path:
    sys.path.append(module_path)