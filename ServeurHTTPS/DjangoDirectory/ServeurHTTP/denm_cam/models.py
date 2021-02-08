from django.db import models

# Create your models here.


class Denm(models.Model):
    chars = models.CharField(
	max_length = 100,
    )


class Cam(models.Model):
    chars = models.CharField(
	max_length = 100,
   )
