# Assets Directory

## Model Files

### digit_model.ptl (Recommended for Android)
PyTorch Mobile Lite format - optimized for Android with smaller size and faster loading.

### digit_model.pt (Fallback)
Full TorchScript model - used as fallback if .ptl is not available.

## Converting Models for Android

To convert a PyTorch TorchScript model to the lite format:

```python
import torch
from torch.utils.mobile_optimizer import optimize_for_mobile

# Load the model
model = torch.jit.load("digit_model.pt", map_location="cpu")
model.eval()

# Optimize for mobile
optimized_model = optimize_for_mobile(model)

# Save as lite interpreter format
optimized_model._save_for_lite_interpreter("digit_model.ptl")
```

## Model Requirements

The digit recognition model should:
- Accept input tensor of shape `[1, 1, 32, 32]` (batch, channel, height, width)
- Output 11 class scores (digits 0-9, plus class 10 for empty cells "E")
- Input is grayscale, normalized with mean=0.5, std=0.5
