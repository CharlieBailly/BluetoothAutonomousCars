from .models import Denm
from .models import Cam
from rest_framework import serializers


class DenmSerializer(serializers.ModelSerializer):
    class Meta:
        model = Denm
        fields = '__all__'

class CamSerializer(serializers.ModelSerializer):
    class Meta:
        model = Cam
        fields = '__all__'

