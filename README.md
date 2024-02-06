# qu_imager

Radiographic technique planning and image quality qualification for the
inspection desk.

We shoot welds. Before a shot goes out this works out the geometry, decays the
source to the day, computes the exposure, picks the image quality indicator and
tells the crew how far to rope the area off. After the shot it grades the image
and says whether it is admissible, then carries the indications through the
acceptance code to a disposition.

It does not display or process images. Pixels belong to the viewer.

## Running the checks

    pip install -e ".[dev]"
    ruff check .
    mypy
    pytest
