import statistics
from statistics import mean

import matplotlib.pyplot as plt


##FOLDER: oldBabelResults
oldBabelResults_latencies=[925.4886, 924.45, 1045.5881, 1375.6278, 1705.1665, 2151.0957, 2447.0374, 2759.0017, 3154.301, 3428.2512]
oldBabelResults_throughputs=[1126.659, 2196.306, 2846.8938, 2878.7334, 2901.368, 2751.675, 2826.2559, 2866.5205, 2817.9333, 2871.789]
oldBabelResults_clients=[1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0]
oldBabelResults_operations=[177516.0, 91062.0, 70252.0, 69475.0, 68933.0, 72683.0, 70765.0, 69771.0, 70974.0, 69643.0]

##FOLDER: quicResults
quicResults_latencies=[1212.2762, 982.2387, 1011.0405, 1252.2673, 1632.6915, 2129.877, 2197.462, 2943.929, 3067.7473, 3369.9844]
quicResults_throughputs=[859.1656, 2066.927, 2957.3987, 3153.9275, 3013.1375, 2783.0344, 3131.8508, 2678.667, 2882.052, 2922.2676]
quicResults_clients=[1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0]
quicResults_operations=[232784.0, 96762.0, 67627.0, 63413.0, 66376.0, 71864.0, 63860.0, 74664.0, 69395.0, 68440.0]

##FOLDER: tcpResulsts
tcpResulsts_latencies=[666.5135, 670.45483, 833.9712, 1095.2002, 1351.4728, 1655.3309, 1884.473, 2195.3289, 2460.0854, 2741.36]
tcpResulsts_throughputs=[1532.027, 3012.366, 3574.3008, 3611.6729, 3659.7864, 3580.2512, 3656.909, 3593.826, 3609.4568, 3589.3113]
tcpResulsts_clients=[1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0]
tcpResulsts_operations=[130546.0, 66393.0, 55955.0, 55376.0, 54648.0, 55862.0, 54691.0, 55651.0, 55410.0, 55721.0]

##FOLDER: udpResults
udpResults_latencies=[791.2243, 644.31116, 756.61127, 978.5949, 1268.5485, 1540.7272, 1954.1176, 2240.7625, 2703.211, 2864.3972]
udpResults_throughputs=[1296.3444, 3158.7097, 3953.8193, 4052.2744, 3912.8223, 3865.8547, 3559.6057, 3555.9348, 3286.825, 3475.6013]
udpResults_clients=[1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0]
udpResults_operations=[154280.0, 63317.0, 50584.0, 49355.0, 51114.0, 51735.0, 56186.0, 56244.0, 60849.0, 57544.0]

"""
arr = latencies
#plt.plot(throughputs,arr,'o',color='b')
plt.plot(throughputs,arr,color='b')

plt.xlabel("x throughputs (ops/sec)")
plt.ylabel("y latencies (us)")

plt.show()
"""


def myPlot(y, x, color, label):
    plt.plot(x, y, color + 'o', label=label)
    plt.legend()
    # plt.axvline(x=3, color='yellow')
    plt.ticklabel_format(axis='y', style='plain', useOffset=False)
    # plt.ylim(0, 1.1)
    # plt.title(protocol)
    plt.xlabel("x throughputs (ops/sec)")
    plt.ylabel("y latencies (us)")


def myPlot2(y, x, color, label):
    plt.plot(x, y, color + 'o', label=label)
    plt.legend()
    # plt.axvline(x=3, color='yellow')
    plt.ticklabel_format(axis='y', style='plain', useOffset=False)
    #plt.ylim(0, 10)
    # plt.title(protocol)
    plt.xlabel("x clients")
    plt.ylabel("y RunTime(ms)")


#    plt.plot(y_12, f_12, '-r', label='0 FAULTS. ')

myPlot(oldBabelResults_latencies, oldBabelResults_throughputs, '-g', 'CURRENT BABEL CHANNEL')
myPlot(tcpResulsts_latencies, tcpResulsts_throughputs, '-y', 'NEW TCP CHANNEL')
myPlot(quicResults_latencies, quicResults_throughputs, '-b', 'NEW QUIC CHANNEL')
myPlot(udpResults_latencies, udpResults_throughputs, '-r', 'NEW UDP CHANNEL')

plt.show()
n_ops=[1,2,3,4,5,6,7,8,9,10]

myPlot2(oldBabelResults_operations, n_ops, '-g','CURRENT BABEL CHANNEL')
myPlot2(tcpResulsts_operations, n_ops, '-y','NEW TCP CHANNEL')
myPlot2(quicResults_operations, n_ops, '-b','NEW QUIC CHANNEL')
myPlot2(udpResults_operations, n_ops, '-r','NEW UDP CHANNEL')
plt.show()

"""

list_avg = mean(throughputs)
standardDev = statistics.stdev(throughputs)
print("Average value of the list:\n")
print(list_avg)
print(standardDev)
print(len(arr))

"""
