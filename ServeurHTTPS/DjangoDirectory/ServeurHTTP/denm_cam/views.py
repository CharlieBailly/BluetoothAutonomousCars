from .models import Denm
from .models import Cam
from .serializers import DenmSerializer
from .serializers import CamSerializer
from django.http import Http404
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status


class DenmList(APIView):

    def get(self, request, format=None):
        denms = Denm.objects.all()
        serializer = DenmSerializer(denms, many=True)
        return Response(serializer.data)

    def post(self, request, format=None):
        print(request.data)
        serializer = DenmSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(serializer.data, status=status.HTTP_201_CREATED)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


class DenmDetail(APIView):
    def get_object(self, pk):
        try:
            return Denm.objects.get(pk=pk)
        except Denm.DoesNotExist:
            raise Http404

    def get(self, request, pk, format=None):
        denm = self.get_object(pk)
        serializer = DenmSerializer(denm)
        return Response(serializer.data)

    def delete(self, request, pk, format=None):
        denm = self.get_object(pk)
        denm.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)


class CamList(APIView):

    def get(self, request, format=None):
        cams = Cam.objects.all()
        serializer = CamSerializer(cams, many=True)
        return Response(serializer.data)

    def post(self, request, format=None):
        serializer = CamSerializer(data=request.data)
        if serializer.is_valid():
            serializer.save()
            return Response(serializer.data, status=status.HTTP_201_CREATED)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


class CamDetail(APIView):
    def get_object(self, pk):
        try:
            return Cam.objects.get(pk=pk)
        except Cam.DoesNotExist:
            raise Http404

    def get(self, request, pk, format=None):
        cam = self.get_object(pk)
        serializer = CamSerializer(cam)
        return Response(serializer.data)

    def delete(self, request, pk, format=None):
        cam = self.get_object(pk)
        cam.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)
